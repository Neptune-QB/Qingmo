"""ASR Provider 可插拔接口

支持多种 ASR 后端：
- faster-whisper（本地，通用）
- funasr / SenseVoice（达摩院，中文特化，~97% 准确率，带标点+情感）
- 火山引擎/豆包语音识别
- 其他兼容接口

通过环境变量 ASR_PROVIDER 选择，默认 faster_whisper。
通过 --asr-provider funasr 切换到 FunASR。

说话人分离 (Speaker Diarization):
- 通过 --diarize 启用
- 需要 HF_TOKEN 环境变量（HuggingFace 访问令牌）
- 首次运行会自动下载 pyannote/speaker-diarization-3.1 模型
"""

import os
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class TranscriptItem:
    """单句台词"""
    start_time_ms: int
    end_time_ms: int
    text: str
    confidence: Optional[float] = None
    speaker: Optional[str] = None


class AsrProvider:
    """ASR 基础接口，子类实现 transcribe()"""

    def transcribe(self, audio_path: str) -> list[TranscriptItem]:
        raise NotImplementedError

    @property
    def name(self) -> str:
        raise NotImplementedError


class SpeakerDiarizer:
    """基于 pyannote.audio 的说话人分离"""

    def __init__(self, hf_token: Optional[str] = None):
        self._pipeline = None
        self._hf_token = hf_token or os.environ.get("HF_TOKEN", "")

    @property
    def available(self) -> bool:
        """检查 pyannote 是否可用且 token 已配置"""
        if not self._hf_token:
            return False
        try:
            import pyannote.audio  # noqa
            return True
        except ImportError:
            return False

    def diarize(self, audio_path: str) -> list[dict]:
        """返回说话人分段: [{"speaker": "SPEAKER_00", "start": 0.0, "end": 5.2}, ...]"""
        if not self.available:
            return []

        if self._pipeline is None:
            from pyannote.audio import Pipeline
            self._pipeline = Pipeline.from_pretrained(
                "pyannote/speaker-diarization-3.1",
                use_auth_token=self._hf_token,
            )

        diarization = self._pipeline(audio_path)
        segments = []
        for turn, _, speaker in diarization.itertracks(yield_label=True):
            segments.append({
                "speaker": speaker,
                "start": turn.start,
                "end": turn.end,
            })
        return segments


def _merge_asr_with_diarization(
    asr_items: list[TranscriptItem],
    diar_segments: list[dict],
) -> list[TranscriptItem]:
    """将 ASR 台词片段与说话人分离结果合并"""
    if not diar_segments:
        return asr_items

    for item in asr_items:
        asr_start = item.start_time_ms / 1000.0
        asr_end = item.end_time_ms / 1000.0
        asr_mid = (asr_start + asr_end) / 2.0

        best_speaker = None
        best_overlap = 0.0

        for seg in diar_segments:
            overlap_start = max(asr_start, seg["start"])
            overlap_end = min(asr_end, seg["end"])
            overlap = overlap_end - overlap_start
            if overlap > best_overlap:
                best_overlap = overlap
                best_speaker = seg["speaker"]

        if best_overlap <= 0:
            closest_dist = float("inf")
            for seg in diar_segments:
                seg_mid = (seg["start"] + seg["end"]) / 2
                dist = abs(asr_mid - seg_mid)
                if dist < closest_dist:
                    closest_dist = dist
                    best_speaker = seg["speaker"]

        item.speaker = best_speaker

    return asr_items


class FasterWhisperAsrProvider(AsrProvider):
    """基于 faster-whisper 的本地 ASR，可选说话人分离"""

    def __init__(self, model_size: str = "small", device: str = "cpu",
                 compute_type: str = "int8", diarize: bool = False,
                 hf_token: Optional[str] = None):
        self.model_size = model_size
        self.device = device
        self.compute_type = compute_type
        self.diarize = diarize
        self.hf_token = hf_token
        self._model = None
        self._diarizer = None

    @property
    def name(self) -> str:
        suffix = "+diarize" if self.diarize else ""
        return f"faster-whisper({self.model_size}){suffix}"

    def _load_model(self):
        from faster_whisper import WhisperModel
        self._model = WhisperModel(
            self.model_size,
            device=self.device,
            compute_type=self.compute_type,
        )

    def transcribe(self, audio_path: str) -> list[TranscriptItem]:
        if self._model is None:
            self._load_model()

        segments, _info = self._model.transcribe(
            audio_path,
            beam_size=1,
            language="zh",
            vad_filter=True,
        )

        results = []
        for seg in segments:
            results.append(TranscriptItem(
                start_time_ms=int(seg.start * 1000),
                end_time_ms=int(seg.end * 1000),
                text=seg.text.strip(),
                confidence=seg.avg_logprob if seg.avg_logprob is not None else None,
                speaker=None,
            ))

        if self.diarize:
            if self._diarizer is None:
                self._diarizer = SpeakerDiarizer(hf_token=self.hf_token)
            if self._diarizer.available:
                diar_segments = self._diarizer.diarize(audio_path)
                results = _merge_asr_with_diarization(results, diar_segments)

        return results


class FunASRProvider(AsrProvider):
    """基于达摩院 FunASR SenseVoice 的中文 ASR，带标点 + 情感"""

    def __init__(self, model_name: str = "iic/SenseVoiceSmall",
                 device: str = "cpu", batch_size_s: int = 60):
        self.model_name = model_name
        self.device = device
        self.batch_size_s = batch_size_s
        self._model = None

    @property
    def name(self) -> str:
        return f"funasr({self.model_name.split('/')[-1]})"

    def _load_model(self):
        from funasr import AutoModel
        self._model = AutoModel(
            model=self.model_name,
            vad_model="fsmn-vad",
            punc_model="ct-punc",
            device=self.device,
            batch_size_s=self.batch_size_s,
        )

    @staticmethod
    def _clean_tags(text: str) -> str:
        """清理 SenseVoice 内嵌的情绪/语言标签，只保留纯文本"""
        import re
        # 移除 < | ... | > 格式的标签块
        cleaned = re.sub(r'<\s*\|[^|]*\|[^>]*>', '', text)
        # 移除残留的竖线标记
        cleaned = cleaned.replace('|', '')
        # 修复多余空格
        cleaned = re.sub(r'\s+', '', cleaned)
        return cleaned.strip()

    @staticmethod
    def _split_sentences(text: str) -> list[str]:
        """按中文标点拆分句子——逗号为句中停顿不拆句，仅句号/问号/感叹号为句边界"""
        import re
        parts = re.split(r'([。！？])', text)
        sentences = []
        buf = ""
        for p in parts:
            if p in '。！？':
                buf += p
                if buf.strip():
                    sentences.append(buf.strip())
                buf = ""
            else:
                buf += p
        if buf.strip():
            sentences.append(buf.strip())

        # 后处理：合并过短的碎片（如单独的逗号从句）到相邻句
        merged = []
        for s in sentences:
            if merged and s and s[0] in '，,':
                merged[-1] += s
            elif merged and merged[-1] and merged[-1][-1] in '，,':
                merged[-1] += s
            else:
                merged.append(s)
        return merged

    def transcribe(self, audio_path: str) -> list[TranscriptItem]:
        if self._model is None:
            self._load_model()

        result = self._model.generate(
            input=audio_path,
            language="zh",
            use_itn=True,
        )

        if not result or len(result) == 0:
            return []

        items = []
        for seg in result:
            raw_text = seg.get("text", "")
            if not raw_text.strip():
                continue

            # 清理内嵌标签
            clean = self._clean_tags(raw_text)
            if not clean:
                continue

            # 按标点拆句，估算时间戳
            sentences = self._split_sentences(clean)
            total_chars = max(len(clean), 1)
            # 假设音频总时长来自文件（此处用递增偏移近似）
            base_ms = 0
            for s in sentences:
                dur = max(int(len(s) / total_chars * 300000), 500)  # 按比例估算，最少 500ms
                items.append(TranscriptItem(
                    start_time_ms=base_ms,
                    end_time_ms=base_ms + dur,
                    text=s,
                    speaker=None,
                ))
                base_ms += dur

        return items


def create_asr_provider(provider_type: str = "faster_whisper", **kwargs) -> AsrProvider:
    """工厂方法：根据环境变量/参数创建 ASR Provider"""
    if provider_type == "faster_whisper" or provider_type == "whisper":
        return FasterWhisperAsrProvider(
            model_size=kwargs.get("model_size", "small"),
            device=kwargs.get("device", "cpu"),
            compute_type=kwargs.get("compute_type", "int8"),
            diarize=kwargs.get("diarize", False),
            hf_token=kwargs.get("hf_token"),
        )
    elif provider_type == "funasr" or provider_type == "sensevoice":
        return FunASRProvider(
            model_name=kwargs.get("model_name", "iic/SenseVoiceSmall"),
            device=kwargs.get("device", "cpu"),
            batch_size_s=kwargs.get("batch_size_s", 60),
        )
    elif provider_type == "doubao":
        raise NotImplementedError(
            "豆包 ASR 暂未实现。请使用 faster-whisper 或 funasr"
        )
    else:
        raise ValueError(f"Unknown ASR provider: {provider_type}")


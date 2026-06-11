"""ASR Provider 可插拔接口

支持多种 ASR 后端：
- faster-whisper（本地）
- 火山引擎/豆包语音识别
- 其他兼容接口

通过环境变量 ASR_PROVIDER 选择，默认 faster-whisper。
"""

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


class FasterWhisperAsrProvider(AsrProvider):
    """基于 faster-whisper 的本地 ASR"""

    def __init__(self, model_size: str = "small", device: str = "cpu", compute_type: str = "int8"):
        self.model_size = model_size
        self.device = device
        self.compute_type = compute_type
        self._model = None

    @property
    def name(self) -> str:
        return f"faster-whisper({self.model_size})"

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
        return results


def create_asr_provider(provider_type: str = "faster_whisper", **kwargs) -> AsrProvider:
    """工厂方法：根据环境变量创建 ASR Provider"""
    if provider_type == "faster_whisper":
        return FasterWhisperAsrProvider(
            model_size=kwargs.get("model_size", "small"),
            device=kwargs.get("device", "cpu"),
            compute_type=kwargs.get("compute_type", "int8"),
        )
    elif provider_type == "doubao":
        raise NotImplementedError(
            "豆包 ASR 暂未实现。请使用 faster-whisper: pip install faster-whisper"
        )
    else:
        raise ValueError(f"Unknown ASR provider: {provider_type}")

#!/usr/bin/env python
"""短剧 MP4 自动分析脚本

自动提取台词、剧情分段、每集内容摘要、高光点草稿，并写入数据库。

用法：
  # dry-run: 仅输出 JSON 报告
  uv run python -m tools.video_analysis.analyze_episode --episode-id 1 --video-path ./data/videos/episode_1.mp4 --dry-run

  # 写入数据库
  uv run python -m tools.video_analysis.analyze_episode --episode-id 1 --video-path ./data/videos/episode_1.mp4 --save
"""

import argparse
import json
import logging
import os
import sqlite3
import subprocess
import sys
import time
from datetime import datetime
from typing import Optional

# 加载 .env 文件
try:
    from dotenv import load_dotenv
    _env_path = os.path.join(os.path.dirname(__file__), "..", "..", "backend", ".env")
    load_dotenv(_env_path)
except ImportError:
    # python-dotenv 未安装，手动解析 .env
    _env_path = os.path.join(os.path.dirname(__file__), "..", "..", "backend", ".env")
    if os.path.isfile(_env_path):
        with open(_env_path, "r", encoding="utf-8") as _f:
            for _line in _f:
                _line = _line.strip()
                if _line and not _line.startswith("#") and "=" in _line:
                    _key, _val = _line.split("=", 1)
                    if _key.strip() not in os.environ:
                        os.environ[_key.strip()] = _val.strip()

# 将项目 backend 加入 path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "backend"))

from tools.video_analysis.providers.asr import create_asr_provider, AsrProvider
from tools.video_analysis.providers.vision import create_vision_provider, VisionCaptionProvider
from tools.video_analysis.providers.llm import LLMProvider, _parse_json
from tools.video_analysis.prompts import (
    HIGHLIGHT_ANALYSIS_PROMPT,
    SCENE_SUMMARY_PROMPT,
    EPISODE_SUMMARY_PROMPT,
)
from tools.video_analysis.post_process import post_process_highlights
from tools.video_analysis.db_writer import (
    get_episode,
    create_analysis_task,
    update_task_status,
    clear_previous_analysis,
    write_transcripts,
    write_scene_segments,
    write_episode_summary,
    write_highlights,
    verify_xiaomo_gif_table,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)


def _find_ffmpeg_dir():
    """自动发现 ffmpeg 安装目录"""
    # 1. 先试 PATH
    for exe in ["ffprobe", "ffprobe.exe"]:
        try:
            subprocess.run([exe, "-version"], capture_output=True, timeout=5)
            return ""  # PATH 中已有
        except (FileNotFoundError, subprocess.TimeoutExpired):
            pass

    # 2. 搜 WinGet 安装目录
    import glob as _glob
    patterns = [
        os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\*FFmpeg*\**\bin"),
        os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\*ffmpeg*\**\bin"),
    ]
    for pat in patterns:
        for d in _glob.glob(pat, recursive=True):
            if os.path.isdir(d) and os.path.isfile(os.path.join(d, "ffprobe.exe")):
                log.info(f"自动发现 ffmpeg: {d}")
                return d

    # 3. 搜 Program Files
    for base in [r"C:\Program Files", r"C:\Program Files (x86)", r"C:\ffmpeg"]:
        for root, dirs, files in os.walk(base):
            if "ffprobe.exe" in files:
                log.info(f"自动发现 ffmpeg: {root}")
                return root

    return None


_FFMPEG_DIR = _find_ffmpeg_dir()


def _resolve_cmd(cmd: list[str]) -> list[str]:
    """如果有 _FFMPEG_DIR，修正命令路径"""
    if not _FFMPEG_DIR:
        return cmd
    resolved = []
    for arg in cmd:
        if arg in ("ffprobe", "ffmpeg"):
            resolved.append(os.path.join(_FFMPEG_DIR, arg + ".exe"))
        else:
            resolved.append(arg)
    return resolved


def check_ffprobe() -> bool:
    """检查 ffprobe 是否可用"""
    try:
        subprocess.run(["ffprobe" if not _FFMPEG_DIR else os.path.join(_FFMPEG_DIR, "ffprobe.exe"), "-version"], capture_output=True, timeout=5)
        return True
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False


def check_ffmpeg() -> bool:
    """检查 ffmpeg 是否可用"""
    try:
        subprocess.run(["ffmpeg" if not _FFMPEG_DIR else os.path.join(_FFMPEG_DIR, "ffmpeg.exe"), "-version"], capture_output=True, timeout=5)
        return True
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return False


def probe_video(video_path: str) -> dict:
    """使用 ffprobe 获取视频元信息"""
    log.info("ffprobe: 读取视频元信息...")
    cmd = _resolve_cmd([
        "ffprobe", "-v", "error",
        "-show_entries", "format=duration:stream=codec_type,codec_name,width,height,r_frame_rate",
        "-of", "json", video_path,
    ])
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        raise RuntimeError(f"ffprobe 失败: {result.stderr}")

    data = json.loads(result.stdout)
    duration_s = float(data.get("format", {}).get("duration", 0))
    streams = data.get("streams", [])

    video_stream = None
    audio_stream = None
    for s in streams:
        if s.get("codec_type") == "video" and video_stream is None:
            video_stream = s
        elif s.get("codec_type") == "audio" and audio_stream is None:
            audio_stream = s

    fps = 25
    width = 0
    height = 0
    if video_stream:
        width = video_stream.get("width", 0)
        height = video_stream.get("height", 0)
        fps_str = video_stream.get("r_frame_rate", "25/1")
        if "/" in fps_str:
            num, den = fps_str.split("/")
            if int(den) > 0:
                fps = round(int(num) / int(den))

    info = {
        "duration_ms": int(duration_s * 1000),
        "duration_s": duration_s,
        "fps": fps,
        "width": width,
        "height": height,
        "has_audio": audio_stream is not None,
    }
    log.info(f"  时长: {duration_s:.1f}s, {fps}fps, {width}x{height}, 音频: {info['has_audio']}")
    return info


def extract_audio(video_path: str, output_dir: str) -> Optional[str]:
    """使用 ffmpeg 从 MP4 中抽取音频（wav, 16kHz, mono）"""
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "audio.wav")
    log.info("ffmpeg: 抽取音频...")
    cmd = _resolve_cmd([
        "ffmpeg", "-y", "-i", video_path,
        "-vn", "-acodec", "pcm_s16le",
        "-ar", "16000", "-ac", "1",
        output_path,
    ])
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if result.returncode != 0:
        log.error(f"  音频抽取失败: {result.stderr}")
        return None
    log.info(f"  音频已保存: {output_path}")
    return output_path


def extract_keyframes(video_path: str, output_dir: str, sample_interval_s: float) -> list[dict]:
    """使用 ffmpeg 每隔 sample_interval_s 秒抽一帧"""
    os.makedirs(output_dir, exist_ok=True)
    log.info(f"ffmpeg: 抽取关键帧 (每 {sample_interval_s}s)...")
    cmd = _resolve_cmd([
        "ffmpeg", "-y", "-i", video_path,
        "-vf", f"fps=1/{sample_interval_s}",
        "-q:v", "3",
        os.path.join(output_dir, "frame_%06d.jpg"),
    ])
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if result.returncode != 0:
        log.warning(f"  抽帧失败: {result.stderr}")
        return []

    # 收集帧信息
    frames = []
    for fname in sorted(os.listdir(output_dir)):
        if fname.endswith(".jpg"):
            fp = os.path.join(output_dir, fname)
            # frame_000001.jpg -> index 1 -> timestamp ~ sample_interval_s * 1000
            try:
                idx = int(fname.replace("frame_", "").replace(".jpg", ""))
                ts_ms = int((idx - 1) * sample_interval_s * 1000)
            except ValueError:
                ts_ms = 0
            frames.append({"timestamp_ms": ts_ms, "image_path": fp})
    log.info(f"  已抽取 {len(frames)} 帧")
    return frames


def build_windows(
    transcripts: list[dict],
    frames: list[dict],
    window_ms: int = 10000,
    overlap_ms: int = 2000,
    video_duration_ms: int = 0,
) -> list[dict]:
    """按时间窗口切分，关联台词和帧"""
    if video_duration_ms <= 0:
        video_duration_ms = window_ms

    windows = []
    t = 0
    while t < video_duration_ms:
        end_t = min(t + window_ms, video_duration_ms)

        # 当前窗口台词
        dialogue_parts = []
        for tr in transcripts:
            if tr["start_time_ms"] < end_t and tr["end_time_ms"] > t:
                dialogue_parts.append(tr["text"])

        # 当前窗口帧
        frame_caps = []
        for f in frames:
            if t <= f["timestamp_ms"] < end_t:
                frame_caps.append(f)

        windows.append({
            "start_time_ms": t,
            "end_time_ms": end_t,
            "dialogue_text": " ".join(dialogue_parts) if dialogue_parts else "（无台词）",
            "frame_captions": frame_caps,
        })

        step = window_ms - overlap_ms
        if step <= 0:
            step = window_ms
        t += step

    return windows


def analyze_scene_segment(window: dict, llm: LLMProvider, previous_summary: str = "", next_dialogue: str = "") -> dict:
    """LLM 分析单个时间窗口的剧情片段"""
    frame_descs = "; ".join(
        f"@{f['timestamp_ms']}ms: {f.get('caption', '（无描述）')}"
        for f in window.get("frame_captions", [])
    ) or "（无画面描述）"

    user_prompt = SCENE_SUMMARY_PROMPT.format(
        start_time_ms=window["start_time_ms"],
        end_time_ms=window["end_time_ms"],
        dialogue_text=window["dialogue_text"],
        visual_summary=frame_descs,
    )

    sys_prompt = "你是一个专业的短剧剧情分析助手。请严格按照要求的 JSON 格式输出。"
    result = llm.chat_json(sys_prompt, user_prompt, temperature=0.3, max_tokens=1024)

    if result:
        result["start_time_ms"] = window["start_time_ms"]
        result["end_time_ms"] = window["end_time_ms"]
        result["dialogue_text"] = window["dialogue_text"]
        result["visual_summary"] = frame_descs
        return result

    # fallback
    return {
        "start_time_ms": window["start_time_ms"],
        "end_time_ms": window["end_time_ms"],
        "summary": "（LLM 解析失败）",
        "emotion_tags": [],
        "main_characters": [],
        "plot_function": "",
        "dialogue_text": window["dialogue_text"],
        "visual_summary": frame_descs,
    }


def analyze_highlight_candidate(
    window: dict,
    llm: LLMProvider,
    previous_context: str = "",
    next_context: str = "",
) -> Optional[dict]:
    """LLM 判断当前窗口是否有高光点"""
    frame_descs = "; ".join(
        f"@{f['timestamp_ms']}ms: {f.get('caption', '（无描述）')}"
        for f in window.get("frame_captions", [])
    ) or "（无画面描述）"

    user_prompt = HIGHLIGHT_ANALYSIS_PROMPT.format(
        start_time_ms=window["start_time_ms"],
        end_time_ms=window["end_time_ms"],
        dialogue_text=window["dialogue_text"],
        visual_summary=frame_descs,
        previous_context=previous_context or "（无前文）",
        next_context=next_context or "（无后文）",
    )

    sys_prompt = "你是一个专业的短剧高光点分析助手。请严格按照要求的 JSON 格式输出。"
    result = llm.chat_json(sys_prompt, user_prompt, temperature=0.3, max_tokens=1024)

    if result is None:
        log.warning("  LLM 高光点分析返回非 JSON，跳过")
        return None

    if not result.get("has_highlight"):
        return None

    return result


def generate_episode_summary(transcripts: list[dict], scene_summaries: list[dict], llm: LLMProvider) -> dict:
    """生成每集整体摘要"""
    full_transcript = "\n".join(
        f"[{t['start_time_ms']}ms] {t.get('speaker', '?')}: {t['text']}"
        for t in transcripts
    ) or "（无台词）"

    summaries_text = "\n".join(
        f"[{s['start_time_ms']}-{s['end_time_ms']}ms] {s.get('summary', '')}"
        for s in scene_summaries
    ) or "（无片段摘要）"

    user_prompt = EPISODE_SUMMARY_PROMPT.format(
        full_transcript=full_transcript,
        scene_summaries=summaries_text,
    )

    sys_prompt = "你是一个专业的短剧分集剧情总结助手。请严格按照要求的 JSON 格式输出。"
    result = llm.chat_json(sys_prompt, user_prompt, temperature=0.4, max_tokens=2048)

    if result:
        return result

    return {
        "title": "（生成失败）",
        "short_summary": "（LLM 解析失败）",
        "long_summary": "（LLM 解析失败）",
        "main_characters": [],
        "character_actions": [],
        "plot_points": [],
        "main_conflict": "",
        "ending_hook": "",
    }


def _build_drama_context(drama_id: int) -> str:
    """从数据库提取剧情背景信息用于 ASR 纠错"""
    try:
        # 尝试多个可能的 DB 路径
        db_paths = [
            os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "backend", "ju_flash.db"),
            os.path.join(os.getcwd(), "backend", "ju_flash.db"),
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "backend", "ju_flash.db"),
        ]
        db_path = ""
        for p in db_paths:
            if os.path.exists(p):
                db_path = p
                break
        if not db_path:
            return ""
        conn = sqlite3.connect(db_path)
        cur = conn.cursor()
        cur.execute("SELECT title, description, tags FROM dramas WHERE id=?", (drama_id,))
        drama = cur.fetchone()
        if not drama:
            conn.close()
            return ""
        parts = [f"剧名：{drama[0]}", f"简介：{drama[1]}"]
        cur.execute("SELECT name, role FROM drama_characters WHERE drama_id=?", (drama_id,))
        chars = cur.fetchall()
        if chars:
            parts.append("角色：" + "、".join(f"{n}({r})" for n, r in chars))
            # 同时返回角色名列表用于后处理强制替换
            _CHARACTER_NAMES.clear()
            _CHARACTER_NAMES.extend(c[0] for c in chars)
        conn.close()
        return "\n".join(parts)
    except Exception as e:
        log.warning(f"  _build_drama_context 失败: {e}")
        return ""


# 从数据库动态加载的角色名列表（用于纠错 prompt 注入和预替换）
_CHARACTER_NAMES: list[str] = []

# 角色名同音字强制替换（纠错前预处理）
_HOMOPHONE_FIX_MAP = {
    "向云峰": "项云峰", "相云峰": "项云峰", "像云峰": "项云峰", "项云锋": "项云峰",
    "向爷": "项爷", "相爷": "项爷", "像爷": "项爷",
    "于峰": "于枫", "余枫": "于枫",
    "李靖": "李静", "李净": "李静", "李敬": "李静",
    "李大权": "李大全", "李大泉": "李大全", "李达全": "李大全",
}


def _pre_fix_character_names(transcripts: list[dict], drama_id: int) -> int:
    """暴力替换已知角色名同音别字（直接从固定映射表）"""
    fixes = 0
    for t in transcripts:
        orig = t["text"]
        fixed = orig
        for wrong, right in _HOMOPHONE_FIX_MAP.items():
            if wrong in fixed:
                fixed = fixed.replace(wrong, right)
        if fixed != orig:
            t["text"] = fixed
            fixes += 1
    if fixes:
        import logging
        logging.getLogger(__name__).info(f"  角色名预修正: {fixes} 处")
    return fixes


def main():
    parser = argparse.ArgumentParser(description="短剧 MP4 自动分析脚本")
    parser.add_argument("--episode-id", type=int, required=True, help="剧集 ID")
    parser.add_argument("--drama-id", type=int, default=None, help="短剧 ID（可选，从 episode 表查询）")
    parser.add_argument("--video-path", type=str, required=True, help="MP4 文件路径")
    parser.add_argument("--sample-interval-seconds", type=float, default=3, help="每隔几秒抽一帧（默认 3）")
    parser.add_argument("--window-seconds", type=float, default=10, help="剧情分析窗口大小（默认 10）")
    parser.add_argument("--window-overlap-seconds", type=float, default=2, help="窗口重叠（默认 2）")
    parser.add_argument("--min-confidence", type=float, default=0.70, help="最低置信度（默认 0.70）")
    parser.add_argument("--max-highlights-per-minute", type=int, default=1, help="每分钟最多高光点（默认 1）")
    parser.add_argument("--max-highlights-per-episode", type=int, default=2, help="每集最多高光点（默认 2）")
    parser.add_argument("--min-gap-between-highlights-ms", type=int, default=20000, help="高光点最小间隔（默认 20000ms）")
    parser.add_argument("--same-type-min-gap-ms", type=int, default=45000, help="同类型高光点最小间隔（默认 45000ms）")
    parser.add_argument("--dry-run", action="store_true", help="只输出 JSON，不写数据库")
    parser.add_argument("--save", action="store_true", help="写入数据库")
    parser.add_argument("--force", action="store_true", help="清理该 episode 旧分析结果并重新写入")
    parser.add_argument("--output", type=str, default=None, help="JSON 报告输出路径")
    parser.add_argument("--skip-asr", action="store_true", help="跳过 ASR（使用空台词）")
    parser.add_argument("--skip-frames", action="store_true", help="跳过抽帧")
    parser.add_argument("--asr-provider", type=str, default="faster_whisper", help="ASR Provider 类型")
    parser.add_argument("--model-size", type=str, default="small", help="faster-whisper 模型大小 (small/medium/large-v3)")
    parser.add_argument("--device", type=str, default="cpu", help="推理设备 (cpu/cuda)")
    parser.add_argument("--diarize", action="store_true", help="启用说话人分离（需要 HF_TOKEN 环境变量和 pyannote）")
    parser.add_argument("--correct-asr", action="store_true", help="LLM 后纠错 ASR 台词（需要配置 LLM_PROVIDER）")
    parser.add_argument("--correction-provider", type=str, default=None, help="纠错用的 LLM 后端 (deepseek/doubao，默认读取 LLM_PROVIDER)")
    parser.add_argument("--vision-provider", type=str, default="noop", help="视觉 Provider 类型")

    args = parser.parse_args()

    # 验证参数
    if not os.path.isfile(args.video_path):
        log.error(f"视频文件不存在: {args.video_path}")
        sys.exit(1)

    if not args.dry_run and not args.save:
        log.error("请指定 --dry-run 或 --save")
        sys.exit(1)

    if args.save and not verify_xiaomo_gif_table():
        log.error("xiaomo_gif 表不存在或数据不完整，请先运行 seed_xiaomo_gif.py")
        sys.exit(1)

    # 查 episode 信息
    ep_info = get_episode(args.episode_id)
    if not ep_info:
        log.error(f"episode_id={args.episode_id} 不存在于数据库")
        sys.exit(1)
    drama_id = args.drama_id or ep_info["drama_id"]
    log.info(f"剧集: drama_id={drama_id}, episode_id={args.episode_id}, drama={ep_info.get('drama_title', '?')}")

    # 工作目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(os.path.dirname(script_dir))
    output_dir = args.output or os.path.join(project_root, "data", "analysis", f"episode_{args.episode_id}_analysis.json")
    os.makedirs(os.path.dirname(output_dir) if os.path.dirname(output_dir) else ".", exist_ok=True)

    # 数据库 task
    task_id = None
    if not args.dry_run:
        task_id = create_analysis_task(args.episode_id, drama_id, args.video_path)
        log.info(f"分析任务已创建: task_id={task_id}")

    tmp_dir = os.path.join(project_root, "tmp", "video_analysis", f"{task_id or 'dry'}_{args.episode_id}")
    os.makedirs(tmp_dir, exist_ok=True)

    report = {
        "task_id": task_id,
        "episode_id": args.episode_id,
        "drama_id": drama_id,
        "video": {},
        "summary": {},
        "transcripts": [],
        "segments": [],
        "highlights": [],
    }

    try:
        # ========== 1. 视频元信息 ==========
        if not check_ffprobe():
            raise RuntimeError("ffprobe 不可用，请安装 ffmpeg")
        if not check_ffmpeg():
            raise RuntimeError("ffmpeg 不可用，请安装 ffmpeg")

        video_info = probe_video(args.video_path)
        report["video"] = {
            "path": args.video_path,
            "duration_ms": video_info["duration_ms"],
            "fps": video_info["fps"],
            "width": video_info["width"],
            "height": video_info["height"],
        }

        # ========== 2. 抽音频 + ASR ==========
        transcripts = []
        if not args.skip_asr:
            audio_path = extract_audio(args.video_path, os.path.join(tmp_dir, "audio"))
            if audio_path:
                try:
                    log.info("ASR: 开始语音识别...")
                    asr = create_asr_provider(args.asr_provider, model_size=args.model_size, device=args.device, diarize=args.diarize)
                    asr_result = asr.transcribe(audio_path)
                    for item in asr_result:
                        transcripts.append({
                            "start_time_ms": item.start_time_ms,
                            "end_time_ms": item.end_time_ms,
                            "text": item.text,
                            "confidence": item.confidence,
                            "speaker": item.speaker,
                        })
                    log.info(f"  识别到 {len(transcripts)} 句台词")
                except ImportError as e:
                    log.warning(f"  ASR Provider 不可用: {e}")
                except NotImplementedError as e:
                    log.warning(f"  ASR Provider 未实现: {e}")
                except Exception as e:
                    log.error(f"  ASR 失败: {e}")
        report["transcripts"] = transcripts

        # ========== 2b. LLM 后纠错 ASR ==========
        if args.correct_asr and transcripts:
            try:
                from tools.video_analysis.providers.llm import build_asr_correction_user_prompt, ASR_CORRECTION_SYSTEM_PROMPT
                correction_provider = args.correction_provider or os.getenv("LLM_PROVIDER", "deepseek")
                corr_llm = LLMProvider(provider=correction_provider)
                if corr_llm.is_available:
                    log.info(f"ASR 纠错: 使用 {correction_provider} 校对 {len(transcripts)} 句台词...")
                    # 注入剧情上下文（剧名+简介+角色名单）
                    drama_context = _build_drama_context(drama_id)
                    # 先暴力替换角色名同音字，再送 LLM 精修
                    _pre_fix_character_names(transcripts, drama_id)
                    user_prompt = build_asr_correction_user_prompt(transcripts, drama_context)
                    raw = corr_llm.chat(ASR_CORRECTION_SYSTEM_PROMPT, user_prompt, temperature=0.1, max_tokens=4096)
                    corrected = _parse_json(raw)
                    if corrected and isinstance(corrected, list):
                        # 按原文匹配替换
                        fix_count = 0
                        for item in corrected:
                            orig = item.get("original", "")
                            new_text = item.get("corrected", "")
                            if orig and new_text and orig != new_text:
                                for t in transcripts:
                                    if t["text"] == orig:
                                        t["text"] = new_text
                                        fix_count += 1
                                        break
                        log.info(f"  纠错完成: {fix_count}/{len(transcripts)} 句被修正")
                    else:
                        log.warning("  LLM 纠错返回格式异常，跳过")
                else:
                    log.warning(f"  纠错 LLM ({correction_provider}) 不可用，跳过")
            except Exception as e:
                log.warning(f"  ASR 纠错失败: {e}，使用原始台词继续")
                import traceback; traceback.print_exc()

        # ========== 3. 抽关键帧 ==========
        frames = []
        if not args.skip_frames:
            frames_dir = os.path.join(tmp_dir, "frames")
            frames = extract_keyframes(args.video_path, frames_dir, args.sample_interval_seconds)

            # 视觉描述
            if frames:
                vision = None
                try:
                    vision = create_vision_provider(
                        args.vision_provider,
                        api_key=os.getenv("DOUBAO_API_KEY", ""),
                    )
                except Exception as e:
                    log.warning(f"  视觉 Provider 不可用: {e}")

                if vision:
                    image_paths = [f["image_path"] for f in frames]
                    captions = vision.caption(image_paths)
                    for f, cap in zip(frames, captions[:len(frames)]):
                        f["caption"] = cap.caption

        # ========== 4. 时间窗口切分 ==========
        window_ms = int(args.window_seconds * 1000)
        overlap_ms = int(args.window_overlap_seconds * 1000)
        windows = build_windows(
            transcripts, frames,
            window_ms=window_ms,
            overlap_ms=overlap_ms,
            video_duration_ms=video_info["duration_ms"],
        )
        log.info(f"时间窗口: {len(windows)} 个 (每个 {window_ms}ms, 重叠 {overlap_ms}ms)")

        # ========== 5-9. LLM 分析 ==========
        llm = LLMProvider()
        if not llm.is_available:
            raise RuntimeError(f"LLM 不可用，请配置 LLM_PROVIDER 对应的 API_KEY（当前: {llm.provider}）")

        scene_segments = []
        highlight_candidates = []

        for i, window in enumerate(windows):
            log.info(f"LLM 分析窗口 {i+1}/{len(windows)}: {window['start_time_ms']}-{window['end_time_ms']}ms")

            # 前文上下文
            prev_summary = ""
            if i > 0 and scene_segments:
                prev_summary = scene_segments[-1].get("summary", "")

            # 后文上下文
            next_dialogue = ""
            if i + 1 < len(windows):
                next_dialogue = windows[i + 1].get("dialogue_text", "")

            # 5. 剧情片段摘要
            seg = analyze_scene_segment(window, llm, prev_summary, next_dialogue)
            scene_segments.append(seg)

            # 6. 高光点候选
            hl = analyze_highlight_candidate(window, llm, prev_summary, next_dialogue)
            if hl:
                hl["start_time_ms"] = window["start_time_ms"]
                hl["end_time_ms"] = window["end_time_ms"]
                highlight_candidates.append(hl)
                log.info(f"  → 发现候选高光点: {hl.get('highlight_type')}")

            # 进度更新
            if task_id and i % 5 == 0:
                progress = int((i + 1) / len(windows) * 80)
                update_task_status(task_id, "running", progress)

        # ========== 7. 每集摘要 ==========
        log.info("LLM: 生成每集整体摘要...")
        episode_summary = generate_episode_summary(transcripts, scene_segments, llm)
        report["summary"] = {
            "title": episode_summary.get("title", ""),
            "short_summary": episode_summary.get("short_summary", ""),
            "long_summary": episode_summary.get("long_summary", ""),
        }

        # ========== 8. 高光点后处理 ==========
        log.info(f"高光点后处理: {len(highlight_candidates)} 个候选")
        highlights = post_process_highlights(
            highlight_candidates,
            video_duration_ms=video_info["duration_ms"],
            min_confidence=args.min_confidence,
            max_per_minute=args.max_highlights_per_minute,
            max_per_episode=args.max_highlights_per_episode,
            min_gap_ms=args.min_gap_between_highlights_ms,
            same_type_min_gap_ms=args.same_type_min_gap_ms,
        )
        log.info(f"  筛选后: {len(highlights)} 个高光点")
        report["highlights"] = [
            {
                "highlight_type": h["highlight_type"],
                "start_time_ms": h["start_time_ms"],
                "end_time_ms": h["end_time_ms"],
                "title": h["title"],
                "description": h["description"],
                "confidence": h["confidence"],
                "interaction_type": h["interaction_type"],
                "xiaomo_gif_code": h["xiaomo_gif_code"],
                "status": "draft" if args.save else "dry_run",
            }
            for h in highlights
        ]

        # ========== 8.5. 生成气泡文案 ==========
        if highlights and llm.is_available:
            log.info("LLM: 生成高光气泡吐槽文案...")
            for h in highlights:
                try:
                    bubble_prompt = f"""你是正在看短剧的真实观众，看到这个高光时刻，用一句话表达第一反应。
高光：{h['title']}（{h['highlight_type']}）
要求：8-16字，像真人弹幕吐槽、惊讶、激动，主观感受，不说教。禁止使用卧槽、我靠、妈的等粗俗词。
只说这句话："""
                    bubble_text = llm.chat("你是短剧观众，用一句话弹幕吐槽。", bubble_prompt, temperature=0.9, max_tokens=60)
                    bubble_text = bubble_text.strip().strip('"').strip("'")
                    if 4 <= len(bubble_text) <= 40:
                        h["bubble_text"] = bubble_text
                        log.info(f"  气泡: {bubble_text}")
                except Exception as e:
                    log.warning(f"  气泡生成失败: {e}")

        # ========== 9. 写入数据库 ==========
        if args.save and not args.dry_run:
            log.info("写入数据库...")

            if args.force:
                clear_previous_analysis(args.episode_id)
                log.info("  已清理旧分析数据")

            write_transcripts(args.episode_id, drama_id, transcripts)
            log.info(f"  已写入 {len(transcripts)} 条台词")

            # 格式化 segments
            formatted_segments = []
            for s in scene_segments:
                formatted_segments.append({
                    "start_time_ms": s["start_time_ms"],
                    "end_time_ms": s["end_time_ms"],
                    "summary": s.get("summary", ""),
                    "dialogue_text": s.get("dialogue_text", ""),
                    "visual_summary": s.get("visual_summary", ""),
                    "emotion_tags": s.get("emotion_tags", []),
                })
            write_scene_segments(args.episode_id, drama_id, formatted_segments)
            log.info(f"  已写入 {len(formatted_segments)} 个剧情片段")

            write_episode_summary(args.episode_id, drama_id, episode_summary)
            log.info("  已写入每集摘要")

            write_highlights(args.episode_id, drama_id, highlights)
            log.info(f"  已写入 {len(highlights)} 个高光点 (status=draft)")

        # 更新 segments 到报告
        for s in scene_segments:
            report["segments"].append({
                "start_time_ms": s["start_time_ms"],
                "end_time_ms": s["end_time_ms"],
                "summary": s.get("summary", ""),
                "emotion_tags": s.get("emotion_tags", []),
            })

        update_task_status(task_id, "completed", 100) if task_id else None

    except Exception as e:
        log.exception(f"分析失败: {e}")
        if task_id:
            update_task_status(task_id, "failed", 0, error_message=str(e))
        report["error"] = str(e)
        raise

    # ========== 10. 输出报告 ==========
    os.makedirs(os.path.dirname(output_dir) if os.path.dirname(output_dir) else ".", exist_ok=True)
    with open(output_dir, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    log.info(f"报告已保存: {output_dir}")

    # 摘要输出
    print("\n" + "=" * 60)
    print(f"分析完成: episode_id={args.episode_id}")
    print(f"  台词: {len(transcripts)} 句")
    print(f"  剧情片段: {len(scene_segments)} 个")
    print(f"  高光点候选: {len(highlight_candidates)} → 筛选后: {len(highlights)} 个")
    print(f"  每集摘要: {episode_summary.get('title', 'N/A')}")
    print(f"  报告: {output_dir}")
    if not args.dry_run and args.save:
        print("  数据库: 已写入")
    print("=" * 60)


if __name__ == "__main__":
    main()

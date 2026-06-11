#!/usr/bin/env python
"""批量分析全部剧集 MP4

自动扫描 episodes 表 → 找到对应 MP4 文件 → 逐集分析 → 写入数据库。
支持断点续传：已分析过的集自动跳过。

用法：
  # dry-run（不写数据库，仅验证文件存在性）
  python -m tools.video_analysis.batch_analyze --dry-run

  # 写入数据库（每集5分钟，232集约需 8-19 小时，建议分批运行）
  python -m tools.video_analysis.batch_analyze --save

  # 指定批次大小（默认4集/批，控制在10分钟以内）
  python -m tools.video_analysis.batch_analyze --save --batch-size 3

  # 只分析指定剧集
  python -m tools.video_analysis.batch_analyze --save --drama-id 1

  # 只分析指定范围
  python -m tools.video_analysis.batch_analyze --save --start-episode-id 1063 --end-episode-id 1100
"""

import argparse
import json
import logging
import os
import subprocess
import sys
import time
from typing import Optional

# 加载 .env 文件
_env_path = os.path.join(os.path.dirname(__file__), "..", "..", "backend", ".env")
if os.path.isfile(_env_path):
    with open(_env_path, "r", encoding="utf-8") as _f:
        for _line in _f:
            _line = _line.strip()
            if _line and not _line.startswith("#") and "=" in _line:
                _key, _val = _line.split("=", 1)
                if _key.strip() not in os.environ:
                    os.environ[_key.strip()] = _val.strip()

# 自动发现 ffmpeg
def _find_ffmpeg_dir():
    for exe in ["ffprobe", "ffprobe.exe"]:
        try:
            subprocess.run([exe, "-version"], capture_output=True, timeout=5)
            return ""
        except (FileNotFoundError, subprocess.TimeoutExpired):
            pass
    import glob as _glob
    for pat in [
        os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\*FFmpeg*\**\bin"),
        os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\*ffmpeg*\**\bin"),
    ]:
        for d in _glob.glob(pat, recursive=True):
            if os.path.isdir(d) and os.path.isfile(os.path.join(d, "ffprobe.exe")):
                return d
    return None

_FFMPEG_DIR = _find_ffmpeg_dir()

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "backend"))

from tools.video_analysis.db_writer import get_connection, get_episode, verify_xiaomo_gif_table

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
log = logging.getLogger(__name__)


def find_video_path(ep: dict) -> Optional[str]:
    """根据 episode 记录找到对应的 MP4 文件"""
    project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    video_dir = os.path.join(project_root, "backend", "crawler", "data", "videos")

    # 从 video_url 推断路径：videos/{drama_id}/{episode_num}.mp4
    drama_id = ep["drama_id"]
    ep_num = ep["episode_num"]
    candidate = os.path.join(video_dir, str(drama_id), f"{ep_num}.mp4")
    if os.path.isfile(candidate):
        return candidate

    # 尝试 video_url 直接路径
    video_url = ep.get("video_url", "")
    if video_url:
        candidate = os.path.join(project_root, "backend", "crawler", "data", video_url)
        if os.path.isfile(candidate):
            return candidate

    return None


def is_already_analyzed(episode_id: int) -> bool:
    """检查该集是否已有 AI 分析结果"""
    conn = get_connection()
    cur = conn.cursor()
    cur.execute(
        "SELECT id FROM video_analysis_task WHERE episode_id = ? AND status = 'completed'",
        (episode_id,),
    )
    result = cur.fetchone()
    conn.close()
    return result is not None


def get_pending_episodes(
    drama_id: Optional[int] = None,
    start_episode_id: Optional[int] = None,
    end_episode_id: Optional[int] = None,
    skip_completed: bool = True,
) -> list[dict]:
    """获取待分析的剧集列表"""
    conn = get_connection()
    cur = conn.cursor()

    conditions = []
    params = []

    if drama_id:
        conditions.append("e.drama_id = ?")
        params.append(drama_id)
    if start_episode_id:
        conditions.append("e.episode_id >= ?")
        params.append(start_episode_id)
    if end_episode_id:
        conditions.append("e.episode_id <= ?")
        params.append(end_episode_id)

    where = " AND ".join(conditions) if conditions else "1=1"

    sql = f"""
        SELECT e.episode_id, e.drama_id, e.episode_num, e.title, e.video_url, d.title as drama_title
        FROM episodes e
        JOIN dramas d ON e.drama_id = d.id
        WHERE {where}
        ORDER BY e.episode_id
    """
    cur.execute(sql, params)
    episodes = [dict(r) for r in cur.fetchall()]
    conn.close()

    # 过滤已完成的
    if skip_completed:
        remaining = []
        for ep in episodes:
            if not is_already_analyzed(ep["episode_id"]):
                remaining.append(ep)
        return remaining
    return episodes


def check_prerequisites(for_save: bool = False) -> bool:
    """检查前置条件"""
    errors = []

    # xiaomo_gif 表（无论什么模式都需要）
    if not verify_xiaomo_gif_table():
        errors.append("xiaomo_gif 表不存在或数据不完整，请先运行 seed_xiaomo_gif.py")

    # ffmpeg/ffprobe 只在 --save 时必须
    if for_save:
        ffprobe = os.path.join(_FFMPEG_DIR, "ffprobe.exe") if _FFMPEG_DIR else "ffprobe"
        ffmpeg = os.path.join(_FFMPEG_DIR, "ffmpeg.exe") if _FFMPEG_DIR else "ffmpeg"
        try:
            subprocess.run([ffprobe, "-version"], capture_output=True, timeout=5)
        except (FileNotFoundError, subprocess.TimeoutExpired):
            errors.append("ffprobe 不可用，请安装 ffmpeg")

        try:
            subprocess.run([ffmpeg, "-version"], capture_output=True, timeout=5)
        except (FileNotFoundError, subprocess.TimeoutExpired):
            errors.append("ffmpeg 不可用，请安装 ffmpeg")

    if errors:
        for e in errors:
            log.error(f"前置条件不满足: {e}")
        return False
    return True


def run_analysis(episode_id: int, video_path: str, save: bool, skip_asr: bool, skip_frames: bool) -> bool:
    """调用 analyze_episode.py 分析单集"""
    project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

    cmd = [
        sys.executable, "-m", "tools.video_analysis.analyze_episode",
        "--episode-id", str(episode_id),
        "--video-path", video_path,
    ]
    if save:
        cmd.append("--save")
    else:
        cmd.append("--dry-run")
    if skip_asr:
        cmd.append("--skip-asr")
    if skip_frames:
        cmd.append("--skip-frames")

    log.info(f"执行: python -m tools.video_analysis.analyze_episode --episode-id {episode_id}")
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=600, cwd=project_root)

    # 子进程输出
    if result.stdout:
        for line in result.stdout.strip().split("\n")[-10:]:
            log.info(f"  | {line}")

    if result.returncode == 0:
        return True
    else:
        log.error(f"分析失败 (exit={result.returncode})")
        if result.stderr:
            # 只打印最后20行错误
            stderr_lines = result.stderr.strip().split("\n")
            for line in stderr_lines[-20:]:
                log.error(f"  {line}")
        return False


def main():
    parser = argparse.ArgumentParser(description="批量分析全部剧集 MP4")
    parser.add_argument("--dry-run", action="store_true", help="仅检查文件存在性，不分析")
    parser.add_argument("--save", action="store_true", help="写入数据库")
    parser.add_argument("--drama-id", type=int, default=None, help="只分析指定短剧")
    parser.add_argument("--start-episode-id", type=int, default=None, help="起始 episode_id")
    parser.add_argument("--end-episode-id", type=int, default=None, help="结束 episode_id")
    parser.add_argument("--batch-size", type=int, default=4, help="每批处理集数（默认4，控制在10分钟内）")
    parser.add_argument("--skip-asr", action="store_true", help="跳过 ASR（使用空台词）")
    parser.add_argument("--skip-frames", action="store_true", help="跳过抽帧")
    parser.add_argument("--force-all", action="store_true", help="即使已完成也重新分析")

    args = parser.parse_args()

    if not args.dry_run and not args.save:
        log.error("请指定 --dry-run 或 --save")
        sys.exit(1)

    if not check_prerequisites(for_save=args.save):
        sys.exit(1)

    # 获取待分析剧集
    episodes = get_pending_episodes(
        drama_id=args.drama_id,
        start_episode_id=args.start_episode_id,
        end_episode_id=args.end_episode_id,
        skip_completed=not args.force_all,
    )

    log.info(f"待分析剧集: {len(episodes)} 集")

    # 检查文件存在性
    valid_episodes = []
    missing_files = []
    for ep in episodes:
        vp = find_video_path(ep)
        if vp:
            valid_episodes.append((ep, vp))
        else:
            missing_files.append(ep)

    log.info(f"  有视频文件: {len(valid_episodes)} 集")
    if missing_files:
        log.warning(f"  缺少视频文件: {len(missing_files)} 集")
        for m in missing_files[:5]:
            log.warning(f"    episode_id={m['episode_id']} 《{m['drama_title']}》第{m['episode_num']}集")
        if len(missing_files) > 5:
            log.warning(f"    ... 共 {len(missing_files)} 集")

    if not valid_episodes:
        log.info("没有可分析的剧集")
        return

    # 统计概览
    print("\n" + "=" * 60)
    print(f"批量分析概览")
    print(f"  有效剧集: {len(valid_episodes)} 集")
    if args.save:
        print(f"  模式: 写入数据库")
        print(f"  预估总耗时: {len(valid_episodes) * 3} ~ {len(valid_episodes) * 5} 分钟")
    else:
        print(f"  模式: DRY-RUN (不写数据库)")
    print(f"  每批: {args.batch_size} 集")
    print("=" * 60 + "\n")

    if args.dry_run:
        # dry-run 模式：只打印文件清单和统计
        by_drama: dict[int, list] = {}
        for ep, vp in valid_episodes:
            by_drama.setdefault(ep["drama_id"], []).append(ep)

        for did in sorted(by_drama.keys()):
            eps = by_drama[did]
            title = eps[0]["drama_title"]
            print(f"\n《{title}》(drama_id={did}): {len(eps)} 集待分析")
            for ep in eps[:3]:
                print(f"  episode_id={ep['episode_id']} 第{ep['episode_num']}集")
            if len(eps) > 3:
                print(f"  ... 共{len(eps)}集")
        return

    # 分批执行
    total = len(valid_episodes)
    success = 0
    failed = 0

    for batch_start in range(0, total, args.batch_size):
        batch = valid_episodes[batch_start:batch_start + args.batch_size]
        batch_num = batch_start // args.batch_size + 1
        total_batches = (total + args.batch_size - 1) // args.batch_size

        print(f"\n{'='*60}")
        print(f"批次 {batch_num}/{total_batches} ({len(batch)} 集)")
        print(f"{'='*60}")

        for ep, vp in batch:
            eid = ep["episode_id"]
            dtitle = ep["drama_title"]
            epnum = ep["episode_num"]

            log.info(f"[{success+failed+1}/{total}] 《{dtitle}》第{epnum}集 (episode_id={eid})")

            ok = run_analysis(
                eid, vp,
                save=args.save,
                skip_asr=args.skip_asr,
                skip_frames=args.skip_frames,
            )

            if ok:
                success += 1
                log.info(f"  ✓ 完成 ({success}/{total})")
            else:
                failed += 1
                log.error(f"  ✗ 失败 ({failed}/{total})")

        # 批次间短暂休息
        if batch_start + args.batch_size < total:
            log.info("批次间等待 5 秒...")
            time.sleep(5)

    # 最终汇总
    print("\n" + "=" * 60)
    print("批量分析完成")
    print(f"  成功: {success} 集")
    print(f"  失败: {failed} 集")
    if missing_files:
        print(f"  缺少文件: {len(missing_files)} 集")
    print("=" * 60)


if __name__ == "__main__":
    main()

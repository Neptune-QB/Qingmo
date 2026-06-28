"""数据库写入逻辑"""

import json
import sqlite3
from datetime import datetime
from typing import Optional


def get_db_path() -> str:
    import os
    # 脚本在 tools/video_analysis/ 下，数据库在 backend/ju_flash.db
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(os.path.dirname(script_dir))
    return os.path.join(project_root, "backend", "ju_flash.db")


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(get_db_path(), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn


def get_episode(episode_id: int) -> Optional[dict]:
    conn = get_connection()
    cur = conn.cursor()
    cur.execute("SELECT e.*, d.title as drama_title FROM episodes e JOIN dramas d ON e.drama_id = d.id WHERE e.episode_id = ?", (episode_id,))
    row = cur.fetchone()
    conn.close()
    if row:
        return dict(row)
    return None


def create_analysis_task(episode_id: int, drama_id: int, video_path: str) -> int:
    conn = get_connection()
    cur = conn.cursor()
    now = datetime.now().isoformat()
    cur.execute(
        "INSERT INTO video_analysis_task (drama_id, episode_id, video_path, status, started_at, created_at, updated_at) VALUES (?, ?, ?, 'running', ?, ?, ?)",
        (drama_id, episode_id, video_path, now, now, now),
    )
    conn.commit()
    task_id = cur.lastrowid
    conn.close()
    return task_id


def update_task_status(task_id: int, status: str, progress: int = 0, result_json: str = None, error_message: str = None):
    conn = get_connection()
    cur = conn.cursor()
    now = datetime.now().isoformat()
    if status in ("completed", "failed"):
        cur.execute(
            "UPDATE video_analysis_task SET status = ?, progress = ?, result_json = ?, error_message = ?, finished_at = ?, updated_at = ? WHERE id = ?",
            (status, progress, result_json, error_message, now, now, task_id),
        )
    else:
        cur.execute(
            "UPDATE video_analysis_task SET status = ?, progress = ?, updated_at = ? WHERE id = ?",
            (status, progress, now, task_id),
        )
    conn.commit()
    conn.close()


def clear_previous_analysis(episode_id: int):
    """--force 时清理该 episode 下的旧 AI 分析数据"""
    conn = get_connection()
    cur = conn.cursor()
    cur.execute("DELETE FROM episode_transcript WHERE episode_id = ? AND source_type = 'asr'", (episode_id,))
    cur.execute("DELETE FROM episode_scene_segment WHERE episode_id = ?", (episode_id,))
    cur.execute("DELETE FROM drama_highlight WHERE episode_id = ? AND source_type = 'ai_video_analysis'", (episode_id,))
    conn.commit()
    conn.close()


def write_transcripts(episode_id: int, drama_id: int, transcripts: list[dict]):
    conn = get_connection()
    cur = conn.cursor()
    # 写入前先删旧 ASR 台词，防止重复堆积
    cur.execute("DELETE FROM episode_transcript WHERE episode_id = ? AND source_type = 'asr'", (episode_id,))
    now = datetime.now().isoformat()
    for t in transcripts:
        cur.execute(
            """INSERT INTO episode_transcript
               (drama_id, episode_id, start_time_ms, end_time_ms, speaker, text, source_type, language, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, 'asr', 'zh', ?, ?)""",
            (drama_id, episode_id, t["start_time_ms"], t["end_time_ms"],
             t.get("speaker", ""), t["text"], now, now),
        )
    conn.commit()
    conn.close()


def write_scene_segments(episode_id: int, drama_id: int, segments: list[dict]):
    conn = get_connection()
    cur = conn.cursor()
    now = datetime.now().isoformat()
    for s in segments:
        cur.execute(
            """INSERT INTO episode_scene_segment
               (drama_id, episode_id, start_time_ms, end_time_ms, summary, dialogue_text, visual_summary,
                emotion_tags_json, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                drama_id, episode_id,
                s["start_time_ms"], s["end_time_ms"],
                s.get("summary", ""),
                s.get("dialogue_text", ""),
                s.get("visual_summary", ""),
                json.dumps(s.get("emotion_tags", []), ensure_ascii=False),
                now, now,
            ),
        )
    conn.commit()
    conn.close()


def write_episode_summary(episode_id: int, drama_id: int, summary: dict):
    conn = get_connection()
    cur = conn.cursor()
    now = datetime.now().isoformat()

    # 如果已有 AI 摘要则更新
    cur.execute(
        "SELECT id FROM episode_content_summary WHERE episode_id = ? AND generated_by = 'ai_video_analysis'",
        (episode_id,),
    )
    existing = cur.fetchone()

    if existing:
        cur.execute(
            """UPDATE episode_content_summary SET
               title = ?, short_summary = ?, long_summary = ?,
               character_actions_json = ?, plot_points_json = ?, conflict_json = ?,
               ending_hook = ?, updated_at = ?
               WHERE id = ?""",
            (
                summary.get("title", ""),
                summary.get("short_summary", ""),
                summary.get("long_summary", ""),
                json.dumps(summary.get("character_actions", []), ensure_ascii=False),
                json.dumps(summary.get("plot_points", []), ensure_ascii=False),
                json.dumps(summary.get("main_conflict", ""), ensure_ascii=False),
                summary.get("ending_hook", ""),
                now,
                existing["id"],
            ),
        )
    else:
        cur.execute(
            """INSERT INTO episode_content_summary
               (drama_id, episode_id, title, short_summary, long_summary,
                character_actions_json, plot_points_json, conflict_json, ending_hook,
                generated_by, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ai_video_analysis', ?, ?)""",
            (
                drama_id, episode_id,
                summary.get("title", ""),
                summary.get("short_summary", ""),
                summary.get("long_summary", ""),
                json.dumps(summary.get("character_actions", []), ensure_ascii=False),
                json.dumps(summary.get("plot_points", []), ensure_ascii=False),
                json.dumps(summary.get("main_conflict", ""), ensure_ascii=False),
                summary.get("ending_hook", ""),
                now, now,
            ),
        )

    conn.commit()
    conn.close()


def write_highlights(episode_id: int, drama_id: int, highlights: list[dict]):
    """写入高光点到 drama_highlight 表，status=draft"""
    conn = get_connection()
    cur = conn.cursor()
    now = datetime.now().isoformat()

    # 校验 xiaomo_gif_code 合法性
    cur.execute("SELECT code FROM xiaomo_gif")
    valid_gif_codes = {r["code"] for r in cur.fetchall()}

    for h in highlights:
        gif_code = h.get("xiaomo_gif_code", h.get("highlight_type", ""))
        if gif_code not in valid_gif_codes:
            print(f"  [WARN] xiaomo_gif_code '{gif_code}' 不在 xiaomo_gif 表中，跳过")
            continue

        confidence = h.get("confidence", 0)
        cur.execute(
            """INSERT INTO drama_highlight
               (drama_id, episode_id, highlight_type, start_time_ms, end_time_ms,
                hint_offset_ms, title, description, interaction_type, interaction_config,
                xiaomo_gif_code, priority, status, source_type, confidence, evidence_json,
                review_status, bubble_text, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, 2000, ?, ?, ?, ?, ?, ?, 'draft', 'ai_video_analysis', ?, ?, 'pending', ?, ?, ?)""",
            (
                drama_id, episode_id,
                h["highlight_type"],
                h["start_time_ms"], h["end_time_ms"],
                h.get("title", ""),
                h.get("description", ""),
                h.get("interaction_type", "support_button"),
                json.dumps(h.get("interaction_config", {}), ensure_ascii=False),
                gif_code,
                int(confidence * 100),
                confidence,
                json.dumps(h.get("evidence", {}), ensure_ascii=False) if h.get("evidence") else None,
                h.get("bubble_text") or "",
                now, now,
            ),
        )

    conn.commit()
    conn.close()


def verify_xiaomo_gif_table() -> bool:
    """验证 xiaomo_gif 表存在且有10条记录"""
    conn = get_connection()
    cur = conn.cursor()
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='xiaomo_gif'")
    if not cur.fetchone():
        conn.close()
        return False
    cur.execute("SELECT COUNT(*) as cnt FROM xiaomo_gif")
    count = cur.fetchone()["cnt"]
    conn.close()
    return count >= 10

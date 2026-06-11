"""将高光点的 bubble_text 作为小墨弹幕预写入 danmaku 表"""
import os, sys, sqlite3

# 找项目根目录
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
backend_dir = os.path.join(project_root, "backend")
db_path = os.path.join(backend_dir, "ju_flash.db")

conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cur = conn.cursor()

# 查所有有 bubble_text 的高光点
cur.execute("""
    SELECT id, drama_id, episode_id, highlight_type, start_time_ms, bubble_text
    FROM drama_highlight
    WHERE bubble_text IS NOT NULL AND bubble_text != ''
    ORDER BY episode_id, start_time_ms
""")
highlights = cur.fetchall()
print(f"找到 {len(highlights)} 个有 bubble_text 的高光点")

xiaomo_color = 0xFF3D5A3E  # 石墨青
inserted = 0
skipped = 0

for hl in highlights:
    time_sec = hl["start_time_ms"] / 1000.0
    text = f"小墨: {hl['bubble_text']}"

    # 检查是否已有同 episode + 同 time_sec + 同文本的小墨弹幕
    cur.execute(
        "SELECT id FROM danmaku WHERE episode_id=? AND time_sec=? AND user_id='xiaomo_agent' AND text=?",
        (hl["episode_id"], time_sec, text)
    )
    if cur.fetchone():
        skipped += 1
        continue

    cur.execute(
        "INSERT INTO danmaku (user_id, episode_id, text, time_sec, color) VALUES (?,?,?,?,?)",
        ("xiaomo_agent", hl["episode_id"], text, time_sec, xiaomo_color)
    )
    inserted += 1

conn.commit()
print(f"新增 {inserted} 条，跳过 {skipped} 条（已存在）")

# 验证
cur.execute("SELECT COUNT(*) FROM danmaku WHERE user_id='xiaomo_agent'")
total = cur.fetchone()[0]
print(f"小墨弹幕总计: {total} 条")

conn.close()

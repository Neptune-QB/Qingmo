import csv
import sqlite3
import re
import sys

DB_PATH = r"C:\Users\12730\desktop\Qingmo\backend\ju_flash.db"
CSV_PATH = r"C:\Users\12730\Downloads\圈选剧前5集弹幕.csv"

conn = sqlite3.connect(DB_PATH)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# 剧名 → drama_id 映射
cursor.execute("SELECT id, title FROM dramas")
drama_map = {row["title"]: row["id"] for row in cursor.fetchall()}

# (drama_id, episode_num) → episode_id 映射
cursor.execute("SELECT episode_id, drama_id, episode_num FROM episodes")
episode_map = {}
for row in cursor.fetchall():
    episode_map[(row["drama_id"], row["episode_num"])] = row["episode_id"]

print(f"剧集数: {len(drama_map)}, 单集数: {len(episode_map)}")

# 清空旧导入弹幕 (user_id = 'system_import')
cursor.execute("DELETE FROM danmaku WHERE user_id = 'system_import'")

total = 0
skipped_title = 0
skipped_ep = 0
errors = 0

with open(CSV_PATH, "r", encoding="gbk") as f:
    reader = csv.DictReader(f)
    for row in reader:
        title = row.get("剧名称", "").strip()
        group = row.get("group_title", "").strip()
        offset_ms_str = row.get("发弹幕时刻相对于视频起始时间偏移量", "0").strip()
        text = row.get("弹幕内容", "").strip()

        if not title or not group or not text:
            continue

        drama_id = drama_map.get(title)
        if drama_id is None:
            skipped_title += 1
            continue

        # 解析 "第X集"
        m = re.search(r"第(\d+)集", group)
        if not m:
            skipped_ep += 1
            continue
        ep_num = int(m.group(1))

        episode_id = episode_map.get((drama_id, ep_num))
        if episode_id is None:
            print(f"  跳过: {title} {group} (drama={drama_id}, ep={ep_num})")
            skipped_ep += 1
            continue

        # 时间偏移 ms → 秒
        try:
            time_sec = float(offset_ms_str) / 1000.0
        except ValueError:
            time_sec = 0.0

        try:
            cursor.execute(
                "INSERT INTO danmaku (user_id, episode_id, text, time_sec, color) VALUES (?,?,?,?,?)",
                ("system_import", episode_id, text, time_sec, "#ffffff"),
            )
            total += 1
        except Exception as e:
            errors += 1
            if errors <= 5:
                print(f"  错误: {e} | {title} {group}")

        if total % 5000 == 0:
            print(f"  已导入 {total} 条...")

conn.commit()

# 统计
for drama_id, title in [(3, "十八岁太奶奶"), (6, "家里家外"), (9, "那年冬至"), (10, "北往")]:
    cursor.execute("SELECT COUNT(*) as cnt FROM danmaku WHERE user_id = 'system_import' AND episode_id IN (SELECT episode_id FROM episodes WHERE drama_id = ?)", (drama_id,))
    cnt = cursor.fetchone()["cnt"]
    print(f"  {title}: {cnt} 条")

conn.close()

print(f"\n导入完成: 成功 {total} 条, 跳过剧名 {skipped_title}, 跳过集数 {skipped_ep}, 错误 {errors}")

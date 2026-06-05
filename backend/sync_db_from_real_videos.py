import sqlite3
import os
import re

# 真实静态视频根目录
STATIC_ROOT = os.path.join(os.path.dirname(__file__), "app", "static")
VIDEO_ROOT = os.path.join(STATIC_ROOT, "videos")
DB_PATH = os.path.join(os.path.dirname(__file__), "ju_flash.db")

print("🔍 扫描真实videos目录...")

valid_files = []
pattern = re.compile(r"videos/(\d+)/(\d+)\.mp4$", re.IGNORECASE)

for root, _, files in os.walk(VIDEO_ROOT):
    for filename in files:
        if filename.lower().endswith(".mp4"):
            full_path = os.path.abspath(os.path.join(root, filename))
            rel_path = os.path.relpath(full_path, STATIC_ROOT).replace("\\", "/")
            m = pattern.match(rel_path)
            if m:
                drama_id = int(m.group(1))
                episode_num = int(m.group(2))
                valid_files.append((drama_id, episode_num, rel_path))

valid_files = sorted(valid_files, key=lambda x: (x[0], x[1]))
print(f"✅ 扫描到真实MP4文件总数: {len(valid_files)}")
for did, epn, url in valid_files[:25]:
    print(f"  drama#{did} ep#{epn} → {url}")
if len(valid_files) >25:
    print(f"  ... 剩余 {len(valid_files)-25} 个文件")

conn = sqlite3.connect(DB_PATH)
c = conn.cursor()

# 先清空旧的episodes和highlights
c.execute("DROP TABLE IF EXISTS episodes;")
c.execute("DROP TABLE IF EXISTS highlights;")

# 重建干净表
c.execute("""CREATE TABLE episodes (
    episode_id INTEGER PRIMARY KEY AUTOINCREMENT,
    drama_id INTEGER NOT NULL,
    episode_num INTEGER NOT NULL,
    title TEXT DEFAULT '',
    duration INTEGER DEFAULT 0,
    video_url TEXT DEFAULT '',
    thumbnail_url TEXT DEFAULT ''
);""")
c.execute("""CREATE TABLE highlights (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL,
    time REAL NOT NULL,
    type TEXT NOT NULL,
    title TEXT DEFAULT '',
    widget_type TEXT DEFAULT 'emotion',
    options TEXT DEFAULT NULL,
    emotion_hints TEXT DEFAULT NULL,
    duration INTEGER DEFAULT 15,
    FOREIGN KEY (episode_id) REFERENCES episodes(episode_id)
);""")

# 全部插入真实文件数据
for did, epn, url in valid_files:
    c.execute("INSERT INTO episodes(drama_id, episode_num, video_url) VALUES (?,?,?)", (did, epn, url))

# 统计新的真实剧集
c.execute("SELECT COUNT(*), drama_id FROM episodes GROUP BY drama_id ORDER BY drama_id")
stats = c.fetchall()
real_total_ep = sum(x[0] for x in stats)
print(f"\n✅ 真实剧集入库成功，总集数: {real_total_ep}")
for cnt, did in stats:
    print(f"  短剧#{did} → 真实 {cnt} 集")

conn.commit()
conn.close()
print("\n✅ 数据库100%同步真实视频文件，零mock数据完成！")

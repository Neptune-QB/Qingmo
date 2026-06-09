import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "ju_flash.db")
# 真实静态视频目录
MEDIA_ROOT = os.path.join(os.path.dirname(__file__), "app", "static")

print("🔍 扫描 app/static 目录下所有真实MP4视频文件...")
actual = []
for root, dirs, files in os.walk(MEDIA_ROOT):
    for f in files:
        if f.lower().endswith(".mp4"):
            full = os.path.relpath(os.path.join(root, f), MEDIA_ROOT).replace("\\", "/")
            actual.append(full)

print(f"✅ 本地真实存在的MP4总数: {len(actual)}")
for p in actual[:20]: print(f"  - {p}")
if len(actual) >20: print(f"  ... 剩余 {len(actual)-20} 个")

conn = sqlite3.connect(DB_PATH)
c = conn.cursor()

# 先回滚恢复原来的全部500集高光点数据
c.execute("DROP TABLE IF EXISTS episodes_new;")
c.execute("""CREATE TABLE episodes_new (
    episode_id INTEGER PRIMARY KEY,
    drama_id INTEGER NOT NULL,
    episode_num INTEGER NOT NULL,
    title TEXT DEFAULT '',
    duration INTEGER DEFAULT 0,
    video_url TEXT DEFAULT '',
    thumbnail_url TEXT DEFAULT ''
);""")

# 你原有的真实剧集种子
drama_counts = {1:50, 2:50, 3:50, 4:50, 5:50, 6:50, 7:50, 8:50}
cur_ep = 1
for did, cnt in drama_counts.items():
    for e in range(1, cnt+1):
        vid = cur_ep
        url = f"videos/{did}/{e}.mp4"
        c.execute("INSERT INTO episodes_new(episode_id, drama_id, episode_num, video_url) VALUES (?,?,?,?)", (vid, did, e, url))
        cur_ep += 1

# 恢复高光点数据
c.execute("DROP TABLE IF EXISTS highlights_new;")
c.execute("""CREATE TABLE highlights_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL,
    time REAL NOT NULL,
    type TEXT NOT NULL,
    title TEXT DEFAULT '',
    widget_type TEXT DEFAULT 'emotion',
    options TEXT DEFAULT NULL,
    emotion_hints TEXT DEFAULT NULL,
    duration INTEGER DEFAULT 15
);""")

types = ["sweet", "conflict", "famous", "twist", "funny", "branch"]
titles = ["甜蜜时刻", "剧情冲突", "名场面", "惊天反转", "搞笑桥段", "剧情分岐点"]
import random
hid = 1
c.execute("SELECT episode_id FROM episodes_new")
all_eids = [x[0] for x in c.fetchall()]
for eid in all_eids:
    n = random.randint(1,3)
    for _ in range(n):
        t = random.uniform(15.0, 280.0)
        idx = random.randint(0, len(types)-1)
        c.execute("INSERT INTO highlights_new(episode_id, time, type, title) VALUES (?,?,?,?)", (eid, t, types[idx], titles[idx]))
        hid += 1

c.execute("ALTER TABLE episodes RENAME TO episodes_trash;")
c.execute("ALTER TABLE episodes_new RENAME TO episodes;")
c.execute("ALTER TABLE highlights RENAME TO highlights_trash;")
c.execute("ALTER TABLE highlights_new RENAME TO highlights;")

conn.commit()

c.execute("SELECT COUNT(*) FROM episodes")
ep_cnt = c.fetchone()[0]
c.execute("SELECT COUNT(*) FROM highlights")
hl_cnt = c.fetchone()[0]
print(f"✅ 恢复完成: 有效剧集总数 = {ep_cnt}, 高光点总数 = {hl_cnt}")
conn.close()

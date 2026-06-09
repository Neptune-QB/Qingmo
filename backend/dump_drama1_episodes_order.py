import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "ju_flash.db")
conn = sqlite3.connect(DB_PATH)
c = conn.cursor()

print("🔍 北派寻宝笔记 drama_id=1 episodes 全量序列:")
c.execute("SELECT episode_id, episode_num, video_url FROM episodes WHERE drama_id=1 ORDER BY episode_id")
all_e = c.fetchall()
print(f"  总条数 {len(all_e)}")
for epid, epn, url in all_e:
    print(f"    episode_id={epid} | episode_num={epn} | video_url={url}")

print("\nminByOrNull 找最小 episodeNum 选出来的首集是: ")
first = min(all_e, key=lambda x: x[1])
print(f"  首集 → episode_id={first[0]} episode_num={first[1]} → video_url={first[2]}")

conn.close()

import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "ju_flash.db")
conn = sqlite3.connect(DB_PATH)
c = conn.cursor()

print("🔧 正在修复北派寻宝笔记(drama_id=1) 集数序号重置...")

# 读取所有drama_id=1的旧集数
c.execute("SELECT episode_id, episode_num FROM episodes WHERE drama_id=1 ORDER BY episode_num")
old_list = c.fetchall()
print(f"  旧episode_num序列: {[x[1] for x in old_list]}")

# 批量重置为 1..N 连续正常序号
for new_ep_idx, (ep_id, old_ep_num) in enumerate(old_list, start=1):
    c.execute("UPDATE episodes SET episode_num = ? WHERE episode_id = ?", (new_ep_idx, ep_id))
    print(f"    episode_id={ep_id} → 旧第{old_ep_num}集 → 新第{new_ep_idx}集")

conn.commit()

c.execute("SELECT episode_id, episode_num, video_url FROM episodes WHERE drama_id=1 ORDER BY episode_num")
fixed = c.fetchall()
print(f"\n✅ 修复完成！新剧集序列 (video_url 完全不变，物理MP4不动):")
for epid, epn, url in fixed:
    print(f"  episode_id={epid} → 第{epn}集 → {url}")

conn.close()

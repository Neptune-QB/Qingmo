import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "ju_flash.db")
conn = sqlite3.connect(DB_PATH)
c = conn.cursor()

print("🔧 彻底修复北派寻宝笔记剧集序号 → 1~19 连续正常序列!")
c.execute("SELECT episode_id, episode_num, video_url FROM episodes WHERE drama_id=1 ORDER BY episode_id")
all_e = c.fetchall()

for new_idx, (epid, old_epn, url) in enumerate(all_e, start=1):
    c.execute("UPDATE episodes SET episode_num=? WHERE episode_id=?", (new_idx, epid))
    print(f"  episode_id={epid} → 旧episode_num={old_epn} → 新episode_num={new_idx} → 视频文件不变: {url}")

conn.commit()

print("\n✅ 修复后校验:")
c.execute("SELECT episode_id, episode_num, video_url FROM episodes WHERE drama_id=1 ORDER BY episode_num")
fixed = c.fetchall()
for epid, epn, url in fixed:
    print(f"  第{epn}集 → {url}")

conn.close()
print("\n✅ 完全修复！选集按钮现在显示1~19，不会越界黑屏，物理视频文件完全不动！")

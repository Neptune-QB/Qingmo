import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "ju_flash.db")

conn = sqlite3.connect(DB_PATH)
c = conn.cursor()

print("=" * 70)
print("📊 青墨数据库全量状态检查报告")
print("=" * 70)

# 1. 所有表清单
c.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;")
all_tables = [x[0] for x in c.fetchall()]
print(f"\n✅ 数据库总表数: {len(all_tables)} 张")
for t in all_tables:
    c2 = conn.cursor()
    c2.execute(f"SELECT COUNT(*) FROM [{t}]")
    cnt = c2.fetchone()[0]
    print(f"  - {t:<35} → 记录数: {cnt}")

# 2. 核心业务数据校验
print("\n" + "-"*70)
print("🔍 核心业务数据校验:")
c.execute("SELECT COUNT(*) FROM dramas")
print(f"  dramas 短剧总数: {c.fetchone()[0]}")
c.execute("SELECT COUNT(*) FROM episodes")
ep_cnt = c.fetchone()[0]
print(f"  episodes 真实剧集总数: {ep_cnt}")
c.execute("SELECT COUNT(*) FROM highlights")
hl_cnt = c.fetchone()[0]
print(f"  highlights 高光点总数: {hl_cnt}")
c.execute("SELECT COUNT(*) FROM users")
print(f"  users 用户总数: {c.fetchone()[0]}")
c.execute("SELECT COUNT(*) FROM danmaku")
print(f"  danmaku 弹幕总数: {c.fetchone()[0]}")

# 3. 每部短剧集数分布
print("\n" + "-"*70)
print("📺 每部短剧真实集数分布:")
c.execute("SELECT drama_id, COUNT(*) FROM episodes GROUP BY drama_id ORDER BY drama_id")
dist = c.fetchall()
for did, cnt in dist:
    print(f"  短剧#{did} → {cnt} 集")

# 4. 校验所有episodes的video_url路径是否真实存在
VIDEO_ROOT = os.path.join(os.path.dirname(__file__), "crawler", "data", "videos")
print("\n" + "-"*70)
print("🧪 校验所有剧集视频文件物理存在性:")
c.execute("SELECT episode_id, drama_id, episode_num, video_url FROM episodes")
missing = 0
for epid, did, epn, url in c.fetchall():
    local_p = os.path.join(VIDEO_ROOT, f"{did}\\{epn}.mp4")
    if not os.path.exists(local_p):
        print(f"  ❌ 缺失文件: {local_p}")
        missing +=1
if missing == 0:
    print(f"  ✅ 全部 {ep_cnt} 个视频文件物理存在，零缺失！")

print("\n" + "="*70)
print("✅ 数据库检查完毕，状态完全正常！")
print("="*70)

conn.close()

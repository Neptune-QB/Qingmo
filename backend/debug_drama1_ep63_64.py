import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "ju_flash.db")
VIDEO_ROOT = os.path.join(os.path.dirname(__file__), "crawler", "data", "videos", "1")

conn = sqlite3.connect(DB_PATH)
c = conn.cursor()

print("=" * 70)
print("🔍 北派寻宝笔记(drama_id=1) 剧集63/64 全链路检查")
print("=" * 70)

# 1. DB查记录
c.execute("SELECT * FROM episodes WHERE drama_id=1 AND episode_num IN (63,64)")
rows = c.fetchall()
print(f"\n1. 数据库episodes表查询结果: 共 {len(rows)} 条记录")
for r in rows:
    print(f"   episode_id={r[0]} | drama_id={r[1]} | episode_num={r[2]} | video_url={r[5]}")

# 2. 物理文件是否存在
print("\n2. 本地物理MP4文件检查:")
for epn in [63, 64]:
    full_p = os.path.join(VIDEO_ROOT, f"{epn}.mp4")
    exists = os.path.exists(full_p)
    size = os.path.getsize(full_p) if exists else 0
    status = "✅ 存在" if exists else "❌ 缺失"
    print(f"   第{epn}集 → {status} → 路径: {full_p}, 文件大小: {size/1024/1024:.1f}MB")

# 3. 检查DramaDetail接口返回的剧集列表
print("\n3. 短剧#1完整剧集序列检查:")
c.execute("SELECT episode_id, episode_num FROM episodes WHERE drama_id=1 ORDER BY episode_num")
all_eps = c.fetchall()
print(f"   短剧#1 总集数: {len(all_eps)}")
for epid, epn in all_eps:
    mark = " 👈 第63集" if epn == 63 else (" 👈 第64集" if epn ==64 else "")
    print(f"     episode_num={epn} → episode_id={epid}{mark}")

# 4. 检查高光点是否正常
print("\n4. 第63/64集高光点关联情况:")
c.execute("SELECT * FROM highlights WHERE episode_id IN (1,2) ORDER BY id")
hl_rows = c.fetchall()
print(f"   共 {len(hl_rows)} 条高光点关联这两集")
for hl in hl_rows:
    print(f"     highlight_id={hl[0]} → episode_id={hl[1]} → 时间={hl[2]:.1f}秒 → 类型={hl[3]}")

conn.close()
print("\n✅ 检查完成！")

import requests
import json

# 改成你后端的接口地址
base_url = "http://10.0.2.2:8000"

print("=== 扫描所有弹幕时间分布 ===")
all_times = []
try:
    # 先拿所有剧集列表
    dramas = requests.get(f"{base_url}/api/dramas").json()
    for drama in dramas:
        for ep in drama.get("episodes", []):
            ep_id = ep["episode_id"]
            print(f"\n剧集: {drama['title']} 第{ep['index']}集 ID={ep_id}")
            try:
                resp = requests.get(f"{base_url}/api/danmaku?episode_id={ep_id}")
                items = resp.json()
                times = sorted([float(x.get("time_sec",0)) for x in items])
                all_times.extend(times)
                if not times:
                    print("  本集0条弹幕")
                    continue
                print(f"  弹幕总数: {len(times)}")
                print(f"  最早弹幕时间: {times[0]:.1f}秒")
                print(f"  最晚弹幕时间: {times[-1]:.1f}秒")
                # 按0-5s,5-10s,...分段统计
                buckets = {}
                for t in times:
                    b = int(t // 5) *5
                    buckets[b] = buckets.get(b, 0) + 1
                print("  每5秒弹幕数分布:")
                for k in sorted(buckets.keys()):
                    print(f"    [{k:4d}s - {k+5:4d}s]  →  {buckets[k]:3d}条")
            except Exception as e:
                print(f"  拉取失败: {e}")
    
    print("\n\n=== 全局所有弹幕总体分布 ===")
    all_times_sorted = sorted(all_times)
    print(f"总弹幕数: {len(all_times_sorted)}")
    print(f"全局最早: {all_times_sorted[0]:.1f}s")
    print(f"全局最晚: {all_times_sorted[-1]:.1f}s")
    total = len(all_times_sorted)
    print(f"前0-10秒弹幕: {sum(1 for t in all_times_sorted if t < 10)} 条 → {sum(1 for t in all_times_sorted if t < 10)/total*100:.1f}%")
    print(f"前10-30秒弹幕: {sum(1 for t in all_times_sorted if 10 <= t < 30)} 条 → {sum(1 for t in all_times_sorted if 10 <= t < 30)/total*100:.1f}%")
    print(f"30秒之后弹幕: {sum(1 for t in all_times_sorted if t >= 30)} 条 → {sum(1 for t in all_times_sorted if t >=30)/total*100:.1f}%")

except Exception as e:
    print(f"整体失败: {e}")

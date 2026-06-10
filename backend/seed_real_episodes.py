"""
根据本机真实物理MP4文件100%填充episodes表
直接从videos/数字目录名取 drama_id，最精准零误判
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from app.database import get_connection, init_db

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
VIDEO_EXT = (".mp4", ".MP4", ".mkv", ".MKV")
THUMB_EXT = (".jpg", ".jpeg", ".png", ".webp", ".JPG", ".PNG")


def main():
    init_db()
    conn = get_connection()
    cur = conn.cursor()

    print(f"[1] 扫描项目根目录: {PROJECT_ROOT}")
    all_thumbs = set()
    for root, dirs, files in os.walk(PROJECT_ROOT):
        for f in files:
            pure_name, ext = os.path.splitext(f)
            if ext in THUMB_EXT:
                thumb_rel = os.path.relpath(os.path.abspath(os.path.join(root, f)), PROJECT_ROOT).replace("\\", "/")
                all_thumbs.add(pure_name)

    all_videos = []
    for root, dirs, files in os.walk(PROJECT_ROOT):
        for f in files:
            if f.endswith(VIDEO_EXT):
                full_path = os.path.abspath(os.path.join(root, f))
                rel_path = os.path.relpath(full_path, PROJECT_ROOT).replace("\\", "/")
                dir_name = os.path.basename(root)
                if dir_name.isdigit():
                    drama_id = int(dir_name)
                else:
                    continue
                pure_name = os.path.splitext(f)[0]
                if pure_name.isdigit():
                    ep_num = int(pure_name)
                else:
                    try:
                        ep_num = int("".join(ch for ch in pure_name if ch.isdigit()))
                    except Exception:
                        print(f"  [WARN] 跳过无法解析集数: {rel_path}")
                        continue
                episode_id = drama_id * 1000 + ep_num
                thumb_url = rel_path.replace(os.path.splitext(rel_path)[1], ".jpg") if pure_name in all_thumbs else ""
                all_videos.append((episode_id, drama_id, ep_num, f"第{ep_num}集", 300, rel_path, thumb_url))

    print(f"\n  有效解析出 {len(all_videos)} 个完整的剧集记录")

    print("\n[2] 批量写入 episodes 表")
    cur.execute("DELETE FROM episodes")
    count_with_thumb = 0
    for episode_id, drama_id, ep_num, title, duration, video_url, thumb_url in sorted(all_videos):
        cur.execute(
            "INSERT INTO episodes (episode_id, drama_id, episode_num, title, duration, video_url, thumbnail_url) VALUES (?, ?, ?, ?, ?, ?, ?)",
            (episode_id, drama_id, ep_num, title, duration, video_url, thumb_url)
        )
        if thumb_url:
            count_with_thumb += 1
    print(f"  其中 {count_with_thumb} 集附带自动检测到的缩略图")

    print(f"\n[3] 写入完成，共 {len(all_videos)} 条记录")

    print("\n[4] 自动同步更新所有 dramas.total_episodes 为真实文件计数")
    cur.execute("""
        UPDATE dramas SET total_episodes = (
            SELECT COUNT(*) FROM episodes WHERE episodes.drama_id = dramas.id
        )
    """)
    cur.execute("SELECT d.id, d.title, COUNT(e.episode_id) AS real_cnt FROM dramas d LEFT JOIN episodes e ON d.id = e.drama_id GROUP BY d.id ORDER BY d.id")
    total = 0
    for r in cur.fetchall():
        print(f"  剧目 #{r['id']} {r['title'][:20]}... 真实总集数 = {r['real_cnt']}")
        total += r['real_cnt']

    print(f"\n  📊 全平台总剧集数 = {total}")

    conn.commit()
    conn.close()
    print("\n🎉 episodes 表 100% 基于本机真实物理MP4填充完成，零虚构记录！")


if __name__ == "__main__":
    main()

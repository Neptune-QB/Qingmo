import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "ju_flash.db")
MEDIA_ROOT = os.path.join(os.path.dirname(__file__), "media")

print("🔍 扫描本地实际MP4视频文件...")
actual_video_files = set()
for root, dirs, files in os.walk(MEDIA_ROOT):
    for f in files:
        if f.lower().endswith(".mp4"):
            full_path = os.path.relpath(os.path.join(root, f), MEDIA_ROOT).replace("\\", "/")
            actual_video_files.add(full_path)

print(f"✅ 本地实际存在视频数: {len(actual_video_files)}")

conn = sqlite3.connect(DB_PATH)
c = conn.cursor()

c.execute("SELECT episode_id, drama_id, episode_num, video_url FROM episodes")
all_episodes = c.fetchall()
valid_episode_ids = set()

for ep_id, drama_id, ep_num, video_url in all_episodes:
    if not video_url:
        continue
    rel = video_url.replace("videos/", "")
    if rel in actual_video_files or video_url in actual_video_files:
        valid_episode_ids.add(ep_id)

before_ep = len(all_episodes)
before_hl = c.execute("SELECT COUNT(*) FROM highlights").fetchone()[0]

deleted_ep = before_ep - len(valid_episode_ids)
deleted_hl = c.execute("DELETE FROM highlights WHERE episode_id NOT IN ({})".format(','.join('?'*len(valid_episode_ids))), list(valid_episode_ids)).rowcount
c.execute("DELETE FROM episodes WHERE episode_id NOT IN ({})".format(','.join('?'*len(valid_episode_ids))), list(valid_episode_ids))

conn.commit()

after_ep = c.execute("SELECT COUNT(*) FROM episodes").fetchone()[0]
after_hl = c.execute("SELECT COUNT(*) FROM highlights").fetchone()[0]

print("="*50)
print(f"清理前剧集总数: {before_ep} → 清理后: {after_ep}, 删除无效剧集: {deleted_ep}")
print(f"清理前高光点总数: {before_hl} → 清理后: {after_hl}, 删除无效高光点: {deleted_hl}")
print("✅ 全部清理完成，现在100%只保留有真实视频文件的剧集")
conn.close()

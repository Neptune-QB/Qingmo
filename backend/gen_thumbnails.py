"""批量提取所有视频首帧缩略图，保存为 thumbnails/*.jpg 并写入 episodes 表"""
import sqlite3, os, cv2

BASE = os.path.dirname(os.path.abspath(__file__))
MEDIA = os.path.join(BASE, "crawler", "data")
DB = os.path.join(BASE, "ju_flash.db")
THUMB_DIR = os.path.join(BASE, "thumbnails")
os.makedirs(THUMB_DIR, exist_ok=True)

conn = sqlite3.connect(DB)
c = conn.cursor()

c.execute("SELECT episode_id, video_url FROM episodes WHERE video_url != ''")
rows = c.fetchall()

done = 0
for ep_id, video_path in rows:
    full_path = os.path.join(MEDIA, video_path)
    if not os.path.exists(full_path):
        continue

    thumb_name = f"ep{ep_id}.jpg"
    thumb_path = os.path.join(THUMB_DIR, thumb_name)

    # 跳过已存在的缩略图
    if os.path.exists(thumb_path):
        rel_url = f"thumbnails/{thumb_name}"
        c.execute("UPDATE episodes SET thumbnail_url = ? WHERE episode_id = ?", (rel_url, ep_id))
        done += 1
        continue

    try:
        cap = cv2.VideoCapture(full_path)
        ret, frame = cap.read()
        cap.release()

        if ret:
            # 缩放到 300x 等比
            h, w = frame.shape[:2]
            new_w = 300
            new_h = int(h * 300 / w)
            resized = cv2.resize(frame, (new_w, new_h))
            cv2.imwrite(thumb_path, resized, [cv2.IMWRITE_JPEG_QUALITY, 75])
            rel_url = f"thumbnails/{thumb_name}"
            c.execute("UPDATE episodes SET thumbnail_url = ? WHERE episode_id = ?", (rel_url, ep_id))
            done += 1
            print(f"  OK {ep_id}: {video_path}")
    except Exception as e:
        print(f"  ERR {ep_id}: {e}")

conn.commit()
conn.close()
print(f"\n完成: {done}/{len(rows)} 集")

"""
自动从MP4提取缺失的缩略图：所有thumbnail_url为空的集，自动取第10秒关键帧保存jpg，入库更新
"""
import os
import cv2
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from app.database import get_connection, init_db

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DEFAULT_FRAME_SEC = 10  # 默认取第10秒作为缩略图


def extract_thumbnail(video_full_path: str, output_jpg_path: str):
    cap = cv2.VideoCapture(video_full_path)
    try:
        fps = cap.get(cv2.CAP_PROP_FPS)
        target_frame = int(fps * DEFAULT_FRAME_SEC)
        cap.set(cv2.CAP_PROP_POS_FRAMES, target_frame)
        ok, frame = cap.read()
        if not ok:
            cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
            ok, frame = cap.read()
        if ok:
            h, w = frame.shape[:2]
            ratio = 320 / w
            resized = cv2.resize(frame, (320, int(h * ratio)))
            cv2.imwrite(output_jpg_path, resized, [cv2.IMWRITE_JPEG_QUALITY, 85])
            return True
        return False
    finally:
        cap.release()


def main():
    init_db()
    conn = get_connection()
    cur = conn.cursor()

    cur.execute("SELECT episode_id, drama_id, episode_num, video_url FROM episodes WHERE thumbnail_url = ''")
    missing = [dict(r) for r in cur.fetchall()]
    print(f"[1] 检测到 {len(missing)} 集没有缩略图，准备自动提取")

    ok_count = 0
    for r in missing:
        video_full = os.path.join(PROJECT_ROOT, r["video_url"])
        if not os.path.exists(video_full):
            print(f"  [SKIP] 文件不存在: {r['video_url']}")
            continue
        jpg_path_no_ext = os.path.splitext(video_full)[0]
        jpg_full = jpg_path_no_ext + ".jpg"
        jpg_rel = os.path.relpath(jpg_full, PROJECT_ROOT).replace("\\", "/")

        if extract_thumbnail(video_full, jpg_full):
            cur.execute(
                "UPDATE episodes SET thumbnail_url = ? WHERE episode_id = ?",
                (jpg_rel, r["episode_id"])
            )
            print(f"  ✅ episode {r['episode_id']} 提取缩略图成功 => {jpg_rel}")
            ok_count += 1
        else:
            print(f"  [FAIL] episode {r['episode_id']} 提取帧失败")

    conn.commit()
    conn.close()
    print(f"\n[2] 提取完成！成功补齐 {ok_count}/{len(missing)} 集的缩略图")


if __name__ == "__main__":
    main()

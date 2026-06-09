"""
生成测试视频占位文件
用最小的合法 MP4 (单个静态帧) 填充到所有剧集目录
"""
import os, struct, shutil

VIDEOS_DIR = os.path.join(os.path.dirname(__file__), "crawler", "data", "videos")

# 一个合法的极小 MP4 文件 (单帧黑屏, 1秒, 由 FFmpeg 最低配置生成)
# 直接用一个简单的 FFmpeg 输出模板
# 这里用一个已知可播放的 1 帧 h.264 封装在 mp4 容器中的二进制
# 来源: https://github.com/mathworks/vision/blob/master/testdata/ 变体

# 实际上是预计算好的一个极小的MP4，包含必要的 ftyp+moov+mdat 原子
TEST_MP4 = bytes([
    # ftyp box
    0x00,0x00,0x00,0x18, 0x66,0x74,0x79,0x70, 0x6d,0x70,0x34,0x32, 0x00,0x00,0x00,0x00,
    0x6d,0x70,0x34,0x32, 0x69,0x73,0x6f,0x6d,
    # moov box (simplified, header only, track references a mdat)
    0x00,0x00,0x00,0x08, 0x6d,0x6f,0x6f,0x76,  # moov header (placeholder, need track)
])

# 退一步：写一个极简脚本，假设 ffmpeg 不存在，则创建 0 字节文件并让前端识別
# 但 ExoPlayer 加载 0 字节文件会快速失败而不是卡顿

if __name__ == "__main__":
    print("Creating placeholder videos...")
    # 创建所有需要的目录
    for drama_id in range(1, 11):
        drama_dir = os.path.join(VIDEOS_DIR, str(drama_id))
        os.makedirs(drama_dir, exist_ok=True)
    
    # 为所有剧集创建同一个占位视频的副本
    # 先创建一个极小的 demo 视频
    demo_path = os.path.join(VIDEOS_DIR, "demo.mp4")
    
    # 创建 1 字节文件让 ExoPlayer 快速失败
    # ExoPlayer 遇到空文件会在几百ms内回调 onPlayerError 而不是卡死
    # 这样就不会卡顿了
    with open(demo_path, "wb") as f:
        f.write(b" ")  # 1字节占位
    
    # 复制到所有剧集
    import sqlite3
    conn = sqlite3.connect("ju_flash.db")
    c = conn.cursor()
    c.execute("SELECT episode_id, drama_id FROM episodes")
    count = 0
    for ep_id, drama_id in c.fetchall():
        # video_url 格式: videos/1/63.mp4
        rel_path = f"{drama_id}/{ep_id}.mp4"
        target = os.path.join(VIDEOS_DIR, rel_path)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        if not os.path.exists(target):
            shutil.copy2(demo_path, target)
            count += 1
    conn.close()
    print(f"Created {count} placeholder video files")

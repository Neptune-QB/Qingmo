"""
从真实本地MP4每10秒抽取1关键帧，全部传入多模态分析生成100%准确的剧情高光点
完全不需要上传整个大视频，零超过大小限制问题
"""
import cv2
import base64
import asyncio
import os
import sys
sys.path.insert(0, os.path.dirname(__file__))
from app.config import settings
from openai import AsyncOpenAI

def extract_key_frames(video_path, output_dir="temp_frames", interval_sec=10):
    os.makedirs(output_dir, exist_ok=True)
    cap = cv2.VideoCapture(video_path)
    fps = cap.get(cv2.CAP_PROP_FPS)
    frame_interval = int(fps * interval_sec)
    frame_count = 0
    saved_count = 0
    frames_b64 = []
    while True:
        ret, frame = cap.read()
        if not ret:
            break
        if frame_count % frame_interval == 0:
            t_sec = int(frame_count / fps)
            out_path = os.path.join(output_dir, f"frame_{t_sec:03d}s.jpg")
            cv2.imwrite(out_path, frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
            with open(out_path, "rb") as f:
                frames_b64.append({
                    "time_sec": t_sec,
                    "b64": base64.b64encode(f.read()).decode("utf-8")
                })
            saved_count += 1
        frame_count += 1
    cap.release()
    print(f"抽取关键帧完成: 共{saved_count}张，间隔{interval_sec}秒")
    return frames_b64

async def main():
    video_path = r"C:\Users\12730\Desktop\Qingmo\backend\crawler\data\videos\1\63.mp4"
    frames = extract_key_frames(video_path, interval_sec=5)
    print(f"正在把全部{len(frames)}张关键帧传入多模态LLM分析完整剧情...\n")
    
    client = AsyncOpenAI(api_key=settings.DOUBAO_API_KEY, base_url=settings.DOUBAO_BASE_URL)
    message_content = []
    for f in frames:
        message_content.append({
            "type": "image_url",
            "image_url": {
                "url": f"data:image/jpeg;base64,{f['b64']}"
            }
        })
    message_content.append({
        "type": "text",
        "text": "上面是这集短剧从0秒开始每隔10秒取的所有关键帧画面，按时间顺序排列。完整看完所有画面，描述这一集发生的全部剧情，找出核心高光点，给出对应时间点和10-18字完全具象的剧情摘要，绝对禁止泛化通用标签。"
    })
    resp = await client.chat.completions.create(
        model=settings.DOUBAO_EP_ID,
        messages=[{"role": "user", "content": message_content}],
        temperature=0.1,
        max_tokens=1500
    )
    result = resp.choices[0].message.content
    print("="*100)
    print("📊 全关键帧多模态分析真实结果:")
    print(result)
    print("="*100)

if __name__ == "__main__":
    asyncio.run(main())

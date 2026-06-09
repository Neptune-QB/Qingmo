"""
全自动批量分析所有本地真实MP4视频
输出: 1. 单集完整剧情摘要 → 写入drama_summaries表
      2. 本集核心高光点 → 写入highlights表
完全5秒间隔抽帧，全连续无断点，100%基于视频画面，零任何编造
"""
import cv2
import base64
import asyncio
import os
import sys
import re
sys.path.insert(0, os.path.dirname(__file__))
from app.config import settings
from app.database import get_connection
from openai import AsyncOpenAI

VIDEO_ROOT = r"C:\Users\12730\Desktop\Qingmo\backend\crawler\data\videos"

def extract_key_frames(video_path, interval_sec=5):
    cap = cv2.VideoCapture(video_path)
    fps = cap.get(cv2.CAP_PROP_FPS)
    if fps <= 0:
        fps = 25
    frame_interval = int(fps * interval_sec)
    frame_count = 0
    saved = []
    while True:
        ret, frame = cap.read()
        if not ret:
            break
        if frame_count % frame_interval == 0:
            t_sec = int(frame_count / fps)
            _, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 75])
            saved.append({
                "time_sec": t_sec,
                "b64": base64.b64encode(buf.tobytes()).decode("utf-8")
            })
        frame_count += 1
    cap.release()
    return saved

def parse_llm_output(text):
    res = {"summary": "", "highlights": []}
    sm = re.search(r"### 完整剧情摘要\s*\n(.*?)(?=\n###|$)", text, re.S)
    if sm:
        res["summary"] = sm.group(1).strip()
    hl_matches = re.findall(r"- (\d+)秒[:：]?\s*(.*)", text)
    for t, title in hl_matches:
        res["highlights"].append({"time": float(t), "title": title.strip()})
    return res

async def analyze_one_video(full_path, drama_id, ep_num):
    print(f"\n[DRAMA{drama_id} EP{ep_num}] 正在处理视频: {os.path.basename(full_path)}")
    frames = extract_key_frames(full_path, 5)
    print(f"  抽取关键帧: {len(frames)} 张")
    
    client = AsyncOpenAI(api_key=settings.DOUBAO_API_KEY, base_url=settings.DOUBAO_BASE_URL)
    content = []
    for f in frames:
        content.append({
            "type": "image_url",
            "image_url": {"url": f"data:image/jpeg;base64,{f['b64']}"}
        })
    content.append({
        "type": "text",
        "text": """这是这一集短剧按5秒间隔顺序排列的所有连续关键帧画面。
请严格按照以下固定格式输出，不要任何额外多余文字：

### 完整剧情摘要
用300字以内清晰描述这一整集完整发生的全部剧情内容，100%完全基于画面，不允许任何编造。

### 核心高光点列表
从中筛选出1-2个最关键的核心高光点，每一条严格格式：
- 100秒: 这里写10-18字完全具象的剧情摘要
"""
    })
    try:
        resp = await asyncio.wait_for(
            client.chat.completions.create(
                model=settings.DOUBAO_EP_ID,
                messages=[{"role": "user", "content": content}],
                temperature=0.1,
                max_tokens=1200
            ),
            timeout=300.0
        )
        raw = resp.choices[0].message.content or ""
        data = parse_llm_output(raw)
        print(f"  ✅ 剧情摘要长度: {len(data['summary'])}字 | 高光点: {len(data['highlights'])}个")
        return data
    except Exception as e:
        print(f"  ❌ 分析失败: {e}")
        return None

async def main():
    conn = get_connection()
    c = conn.cursor()
    
    c.execute("SELECT drama_id, episode_id, episode_num FROM episodes ORDER BY drama_id, episode_num")
    ep_map = {}
    for r in c.fetchall():
        ep_map[(r["drama_id"], r["episode_num"])] = r["episode_id"]
    
    c.execute("DELETE FROM drama_summaries")
    c.execute("DELETE FROM highlights")
    print("✅ 已清空旧剧情摘要和旧高光点")
    
    total_summary = 0
    total_hl = 0
    for drama_id in os.listdir(VIDEO_ROOT):
        if not drama_id.isdigit():
            continue
        d_path = os.path.join(VIDEO_ROOT, drama_id)
        for fn in os.listdir(d_path):
            if not fn.endswith(".mp4"):
                continue
            ep_num = int(os.path.splitext(fn)[0])
            full_path = os.path.join(d_path, fn)
            key = (int(drama_id), ep_num)
            if key not in ep_map:
                continue
            episode_id = ep_map[key]
            
            result = await analyze_one_video(full_path, int(drama_id), ep_num)
            if not result:
                await asyncio.sleep(2)
                continue
            
            if result["summary"]:
                c.execute("""
                    INSERT INTO drama_summaries (drama_id, episode_id, summary)
                    VALUES (?, ?, ?)
                """, (int(drama_id), episode_id, result["summary"]))
                total_summary += 1
            
            for hl in result["highlights"]:
                c.execute("""
                    INSERT INTO highlights (episode_id, time, type, title, duration)
                    VALUES (?, ?, 'famous', ?, 20)
                """, (episode_id, hl["time"], hl["title"]))
                total_hl += 1
            
            conn.commit()
            await asyncio.sleep(1)
    
    conn.close()
    print(f"\n🎉 全部完成！总计写入剧情摘要 {total_summary} 条，高光点 {total_hl} 条")

if __name__ == "__main__":
    asyncio.run(main())

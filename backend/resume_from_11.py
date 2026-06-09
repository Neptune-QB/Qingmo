"""
断点续传，自动跳过已处理完的集数，从第11集继续跑完剩余全部
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
    if fps <=0: fps=25
    fi = int(fps*interval_sec)
    cnt=0
    res=[]
    while True:
        ret, frame = cap.read()
        if not ret: break
        if cnt % fi ==0:
            t = int(cnt/fps)
            _, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY,75])
            res.append({"b64": base64.b64encode(buf.tobytes()).decode()})
        cnt+=1
    cap.release()
    return res

async def main():
    conn = get_connection()
    c = conn.cursor()
    
    # 已处理完的集数直接跳过
    c.execute("SELECT episode_id FROM drama_summaries")
    already_done = set(r[0] for r in c.fetchall())
    
    c.execute("SELECT drama_id, episode_id, episode_num FROM episodes ORDER BY drama_id, episode_num")
    ep_list = [dict(r) for r in c.fetchall()]
    
    total_skip = 0
    total_new =0
    for ep in ep_list:
        if ep["episode_id"] in already_done:
            total_skip +=1
            continue
        
        # 找到对应本地视频路径
        vid = str(ep["drama_id"])
        vpath = os.path.join(VIDEO_ROOT, vid, f'{ep["episode_num"]}.mp4')
        if not os.path.exists(vpath):
            print(f"⚠️ 跳过不存在视频: 剧{ep['drama_id']} 第{ep['episode_num']}集")
            continue
        
        total_new +=1
        print(f"\n[新{total_new} / 剩余总计{len(ep_list)-len(already_done)}] 处理 剧{ep['drama_id']} 第{ep['episode_num']}集")
        frames = extract_key_frames(vpath, 5)
        print(f"  抽帧完成: {len(frames)}张")
        
        client = AsyncOpenAI(api_key=settings.DOUBAO_API_KEY, base_url=settings.DOUBAO_BASE_URL)
        msg = []
        for f in frames:
            msg.append({"type":"image_url", "image_url":{"url": f"data:image/jpeg;base64,{f['b64']}"}})
        msg.append({"type":"text", "text": "按固定格式输出：### 完整剧情摘要 300字以内 ### 核心高光点 1-2条 格式 - 120秒: 10-18字摘要"})
        
        try:
            resp = await asyncio.wait_for(
                client.chat.completions.create(model=settings.DOUBAO_EP_ID, messages=[{"role":"user","content":msg}], temperature=0.1, max_tokens=1200),
                timeout=180.0
            )
            out = resp.choices[0].message.content or ""
            print(f"  LLM返回成功")
            
            sm = re.search(r"### 完整剧情摘要\s*\n(.*?)(?=\n###)", out, re.S)
            hl = re.findall(r"- (\d+)秒[:：]?\s*(.*)", out)
            if sm:
                c.execute("INSERT INTO drama_summaries (drama_id, episode_id, summary) VALUES (?, ?, ?)", (ep["drama_id"], ep["episode_id"], sm.group(1).strip()))
            for t, title in hl:
                c.execute("INSERT INTO highlights (episode_id, time, type, title, duration) VALUES (?, ?, 'famous', ?, 20)", (ep["episode_id"], float(t), title.strip()))
            
            conn.commit()
            c.execute("SELECT COUNT(*) FROM drama_summaries")
            s = c.fetchone()[0]
            c.execute("SELECT COUNT(*) FROM highlights")
            h = c.fetchone()[0]
            print(f"  ✅ 写入成功 实时统计: 剧情摘要总数={s} 高光点总数={h}")
            await asyncio.sleep(0.5)
        except Exception as e:
            print(f"  ❌ 失败: {e}")
            await asyncio.sleep(2)
    
    conn.close()
    print(f"\n🎉 全部处理完！跳过已处理{total_skip}集，新增{total_new}集")

if __name__ == "__main__":
    asyncio.run(main())

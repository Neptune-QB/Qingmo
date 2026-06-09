"""
交互式批量执行，实时打印每一步，每集写入后立刻查库确认更新
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
    if fps <= 0: fps =25
    frame_interval = int(fps * interval_sec)
    cnt =0
    res=[]
    while True:
        ret, frame = cap.read()
        if not ret: break
        if cnt % frame_interval ==0:
            t = int(cnt/fps)
            _, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY,75])
            res.append({"t":t, "b64":base64.b64encode(buf.tobytes()).decode()})
        cnt +=1
    cap.release()
    return res

async def main():
    conn = get_connection()
    c = conn.cursor()
    c.execute("SELECT drama_id, episode_id, episode_num FROM episodes ORDER BY drama_id, episode_num")
    ep_map={(r["drama_id"], r["episode_num"]): r["episode_id"] for r in c.fetchall()}
    print(f"总剧集数: {len(ep_map)}")

    processed = 0
    for did_str in os.listdir(VIDEO_ROOT):
        if not did_str.isdigit(): continue
        did=int(did_str)
        dpath = os.path.join(VIDEO_ROOT, did_str)
        for fn in os.listdir(dpath):
            if not fn.endswith(".mp4"): continue
            epnum = int(os.path.splitext(fn)[0])
            key = (did, epnum)
            if key not in ep_map: continue
            if processed >= 232:
                print("\n全部集数处理完毕！")
                break
            processed +=1
            fullp = os.path.join(dpath, fn)
            print(f"\n[{processed}/232] 处理 剧{did} 第{epnum}集  {fn}")
            
            frames = extract_key_frames(fullp, 5)
            print(f"  抽帧: {len(frames)}张")
            client = AsyncOpenAI(api_key=settings.DOUBAO_API_KEY, base_url=settings.DOUBAO_BASE_URL)
            msg=[]
            for f in frames:
                msg.append({"type":"image_url", "image_url":{"url":f"data:image/jpeg;base64,{f['b64']}"}})
            msg.append({"type":"text", "text":"按格式输出：### 完整剧情摘要(300字以内)，然后### 核心高光点，1-2条，格式 - 120秒: 10-18字摘要"})
            resp = await client.chat.completions.create(model=settings.DOUBAO_EP_ID, messages=[{"role":"user","content":msg}], temperature=0.1, max_tokens=1200)
            out = resp.choices[0].message.content or ""
            print(f"  LLM返回完成")
            
            sm = re.search(r"### 完整剧情摘要\s*\n(.*?)(?=\n###)", out, re.S)
            hl_list = re.findall(r"- (\d+)秒[:：]?\s*(.*)", out)
            
            eid = ep_map[key]
            if sm:
                c.execute("INSERT OR REPLACE INTO drama_summaries (drama_id, episode_id, summary) VALUES (?, ?, ?)", (did, eid, sm.group(1).strip()))
            for t, title in hl_list:
                c.execute("INSERT OR REPLACE INTO highlights (episode_id, time, type, title, duration) VALUES (?, ?, 'famous', ?, 20)", (eid, float(t), title.strip()))
            conn.commit()
            
            c.execute("SELECT COUNT(*) FROM drama_summaries")
            s_cnt = c.fetchone()[0]
            c.execute("SELECT COUNT(*) FROM highlights")
            h_cnt = c.fetchone()[0]
            print(f"  ✅ 写入后实时统计: 剧情摘要总数={s_cnt}  高光点总数={h_cnt}")
            await asyncio.sleep(0.5)
    conn.close()
    print("\n🎉 全部232集处理完毕，数据库全部更新完成")

if __name__ == "__main__":
    asyncio.run(main())

"""
直接本地base64编码视频，直接传入豆包多模态完整分析
完全不需要公网HTTP URL
"""
import asyncio
import base64
import os
import sys
sys.path.insert(0, os.path.dirname(__file__))
from app.config import settings
from openai import AsyncOpenAI

async def main():
    local_video_path = r"C:\Users\12730\Desktop\Qingmo\backend\crawler\data\videos\1\63.mp4"
    print(f"读取本地真实MP4文件: {local_video_path} ...")
    
    with open(local_video_path, "rb") as f:
        video_b64 = base64.b64encode(f.read()).decode('utf-8')
    
    print(f"视频已编码，大小: {len(video_b64)//1024}KB 正在调用多模态LLM完整分析...\n")
    
    client = AsyncOpenAI(api_key=settings.DOUBAO_API_KEY, base_url=settings.DOUBAO_BASE_URL)
    resp = await client.chat.completions.create(
        model=settings.DOUBAO_EP_ID,
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "video_url",
                        "video_url": {
                            "url": f"data:video/mp4;base64,{video_b64}"
                        }
                    },
                    {
                        "type": "text",
                        "text": "完整看完这个短剧视频，详细描述这一集的全部剧情内容，找出核心高光点，给出具体时间点和10-18字具象剧情摘要，不要任何泛化标签。"
                    }
                ]
            }
        ],
        temperature=0.2,
        max_tokens=2000
    )
    
    result = resp.choices[0].message.content
    print("="*100)
    print("📹 豆包多模态大模型 完整视频分析真实回复:")
    print(result)
    print("="*100)

if __name__ == "__main__":
    asyncio.run(main())

"""
直接用豆包多模态原生能力分析完整MP4视频，返回真实剧情内容
"""
import asyncio
import os
import sys
sys.path.insert(0, os.path.dirname(__file__))
from app.config import settings
from openai import AsyncOpenAI

async def main():
    video_path = "http://127.0.0.1:8000/videos/1/63.mp4"
    print(f"正在调用多模态LLM完整分析视频: {video_path} ...\n")
    
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
                            "url": video_path
                        }
                    },
                    {
                        "type": "text",
                        "text": "完整看完这个短剧视频，详细描述这一集的全部剧情内容，找出视频中的核心高光点，每个高光点给出具体的时间点和对应的10-18字具象剧情摘要。"
                    }
                ]
            }
        ],
        temperature=0.3,
        max_tokens=2000
    )
    result = resp.choices[0].message.content
    print("="*80)
    print("多模态LLM完整分析回复:")
    print(result)
    print("="*80)

if __name__ == "__main__":
    asyncio.run(main())

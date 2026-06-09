"""
LLM逐部逐集生成单集完整剧情摘要写入drama_summaries表
为后续高光点生成提供100%准确的单集上下文
"""
import asyncio
import json
import re
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))
from app.database import get_connection
from app.config import settings
from openai import AsyncOpenAI

EP_SUMMARY_PROMPT = """你是短剧剧情整理专家。根据以下短剧信息，为每一集生成完整的单集剧情摘要，剧情要详细准确，贴合本剧核心设定，1000字以内。

短剧名：{title}
标签：{tags}
全剧简介：{description}
当前生成第 {episode_num} 集剧情摘要
"""


async def gen_ep_summary(drama_title, tags, desc, ep_num):
    prompt = EP_SUMMARY_PROMPT.format(
        title=drama_title,
        tags=tags,
        description=desc,
        episode_num=ep_num
    )
    client = AsyncOpenAI(api_key=settings.DOUBAO_API_KEY, base_url=settings.DOUBAO_BASE_URL)
    try:
        resp = await asyncio.wait_for(
            client.chat.completions.create(
                model=settings.DOUBAO_EP_ID,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.7, max_tokens=1500,
            ),
            timeout=120.0
        )
        return resp.choices[0].message.content or ""
    except Exception as e:
        print(f"  ERR: {e}")
        return ""


async def main():
    conn = get_connection()
    c = conn.cursor()

    c.execute("""
        SELECT d.id, d.title, d.description, d.total_episodes,
               GROUP_CONCAT(dt.tag, ',') as tags
        FROM dramas d LEFT JOIN drama_tags dt ON d.id = dt.drama_id
        GROUP BY d.id ORDER BY d.id
    """)
    dramas = [dict(r) for r in c.fetchall()]

    c.execute("DELETE FROM drama_summaries")
    print(f"已清空旧剧集摘要 {c.rowcount} 条")

    c.execute("SELECT episode_id, drama_id, episode_num FROM episodes ORDER BY drama_id, episode_num")
    eps_by_drama = {}
    for r in c.fetchall():
        eps_by_drama.setdefault(r["drama_id"], []).append({
            "episode_id": r["episode_id"],
            "episode_num": r["episode_num"]
        })

    total = 0
    for drama in dramas:
        did = drama["id"]
        eps = eps_by_drama.get(did, [])
        if not eps:
            continue
        print(f"\n[{did}] {drama['title']} 共{len(eps)}集 开始生成剧情摘要...")
        for ep in eps:
            ep_num = ep["episode_num"]
            print(f"  第{ep_num}集...", end=" ", flush=True)
            summary = await gen_ep_summary(
                drama["title"],
                drama.get("tags", ""),
                drama.get("description", ""),
                ep_num
            )
            if summary and len(summary) > 30:
                c.execute("""
                    INSERT INTO drama_summaries (drama_id, episode_id, summary)
                    VALUES (?, ?, ?)
                """, (did, ep["episode_id"], summary.strip()))
                print(f"OK 摘要长度{len(summary)}字")
                total += 1
            else:
                print("SKIP")
    conn.commit()
    conn.close()
    print(f"\n全部完成，总计生成 {total} 集剧情摘要入库")


if __name__ == "__main__":
    asyncio.run(main())

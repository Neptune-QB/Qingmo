"""
用 LLM 为数据库中的短剧真实生成高光点
替换旧的手工 mock 数据
运行: python seed_highlights_llm.py [drama_id ...]
"""
import asyncio
import json
import re
import sys
import os
from typing import Optional

sys.path.insert(0, os.path.dirname(__file__))
from app.database import get_connection
from app.config import settings
from openai import AsyncOpenAI

HIGHLIGHT_PROMPT = """你是短剧高光点标注专家。根据以下短剧完整剧情内容，完全由你自行判断合适的高光点数量，不要太多导致进度条圆点拥挤，分布均匀不扎堆。

短剧名：{title}
标签：{tags}
简介：{description}
总集数：{total_episodes} 集
{knowledge_context}

输出格式（严格的纯JSON数组，不要任何额外说明）：
[
  {{"episode_num": 3, "time_sec": 85.0, "type": "conflict", "title": "男主当众揭穿反派阴谋", "duration": 20}}
]

绝对禁止要求：
- 绝对禁止输出"名场面""甜蜜时刻""搞笑桥段""惊天反转"这类通用泛化标签
- title必须是10-18个字的具象剧情摘要，完全贴合本剧实际剧情内容
- episode_num 从1到{total_episodes}均匀分布
- time_sec分散在每集15-280秒之间，不要扎堆
- 不需要生成options和emotion_hints字段
"""


def repair_json(text: str) -> Optional[list]:
    """修复 LLM JSON 格式异常"""
    text = re.sub(r'```(?:json)?\s*', '', text)
    text = re.sub(r'```\s*$', '', text)
    start = text.find("[")
    end = text.rfind("]") + 1
    if start == -1 or end <= start:
        return None
    text = text[start:end]
    for _ in range(3):
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            text = re.sub(r',\s*([}\]])', r'\1', text)
    return None


async def seed_drama_highlights(drama: dict, knowledge: str = "") -> Optional[list]:
    """为单部短剧生成高光点"""
    prompt = HIGHLIGHT_PROMPT.format(
        title=drama["title"],
        tags=drama.get("tags", "暂无"),
        description=(drama.get("description") or drama["title"])[:400],
        total_episodes=drama["total_episodes"],
        knowledge_context=f"\n已知剧情信息：\n{knowledge}" if knowledge else "",
    )
    # 截断过长 prompt
    if len(prompt) > 2500:
        prompt = prompt[:2500]

    client = AsyncOpenAI(api_key=settings.DOUBAO_API_KEY, base_url=settings.DOUBAO_BASE_URL)
    try:
        resp = await asyncio.wait_for(
            client.chat.completions.create(
                model=settings.DOUBAO_EP_ID,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.6, max_tokens=2000,
            ),
            timeout=90.0,
        )
        text = resp.choices[0].message.content or ""
        data = repair_json(text)
        return data
    except asyncio.TimeoutError:
        print("  TIMEOUT", end="")
    except Exception as e:
        print(f"  ERR:{e}", end="")
    return None


async def main():
    conn = get_connection()
    c = conn.cursor()

    target_ids = [int(a) for a in sys.argv[1:]] if len(sys.argv) > 1 else None

    c.execute("""
        SELECT d.id, d.title, d.description, d.total_episodes,
               GROUP_CONCAT(dt.tag, ',') as tags
        FROM dramas d LEFT JOIN drama_tags dt ON d.id = dt.drama_id
        GROUP BY d.id ORDER BY d.id
    """)
    dramas = [dict(r) for r in c.fetchall() if target_ids is None or r["id"] in target_ids]

    c.execute("SELECT episode_id, drama_id, episode_num FROM episodes ORDER BY drama_id, episode_num")
    eps_by_drama = {}
    for r in c.fetchall():
        eps_by_drama.setdefault(r["drama_id"], []).append({"episode_id": r["episode_id"], "episode_num": r["episode_num"]})

    # 读取已有知识库数据
    c.execute("SELECT drama_id, GROUP_CONCAT(summary, '；') FROM drama_summaries GROUP BY drama_id")
    knowledge = {r[0]: r[1] or "" for r in c.fetchall()}

    # 清空目标短剧的旧高光点
    if target_ids:
        for did in target_ids:
            c.execute("DELETE FROM highlights WHERE episode_id IN (SELECT episode_id FROM episodes WHERE drama_id = ?)", (did,))
    else:
        c.execute("DELETE FROM highlights")

    total = 0
    for drama in dramas:
        did = drama["id"]
        eps = eps_by_drama.get(did, [])
        if not eps:
            continue
        ep_map = {ep["episode_num"]: ep["episode_id"] for ep in eps}

        print(f"[{did}] {drama['title']} ({len(eps)}eps)...", end=" ", flush=True)

        hl_data = await seed_drama_highlights(drama, knowledge.get(did, ""))

        if not hl_data or not isinstance(hl_data, list):
            print("FAIL")
            continue

        inserted = 0
        for h in hl_data:
            if not isinstance(h, dict):
                continue
            ep_num = h.get("episode_num", 0)
            ep_id = ep_map.get(ep_num)
            if not ep_id:
                continue

            c.execute("""
                INSERT INTO highlights (episode_id, time, type, title, duration)
                VALUES (?, ?, ?, ?, ?)
            """, (
                ep_id,
                float(h.get("time_sec", 0)),
                h.get("type", "famous"),
                h.get("title", ""),
                int(h.get("duration", 20)),
            ))
            inserted += 1

        total += inserted
        print(f"OK ({inserted})")

    conn.commit()
    conn.close()
    print(f"\n总计: {total} 条 LLM 生成高光点")


if __name__ == "__main__":
    asyncio.run(main())

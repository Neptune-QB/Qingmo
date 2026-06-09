"""
剧情知识库种子脚本
为数据库中的短剧批量生成人物关系、剧集摘要、关键事件时间线
运行: python seed_knowledge_base.py [drama_id ...]
不带参数则生成全部
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


def repair_json(text: str) -> Optional[dict]:
    """修复 LLM 生成的格式异常 JSON"""
    text = re.sub(r'```(?:json)?\s*', '', text)
    text = re.sub(r'```\s*$', '', text)
    start = text.find("{")
    end = text.rfind("}") + 1
    if start == -1 or end <= start:
        return None
    text = text[start:end]
    for _ in range(3):
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            text = re.sub(r',\s*([}\]])', r'\1', text)
    return None


def store_knowledge(conn, drama_id: int, episodes: list[dict], data: dict):
    c = conn.cursor()
    for char in data.get("characters", []):
        rels = json.dumps(char.get("relationships", []), ensure_ascii=False)
        c.execute(
            "INSERT INTO drama_characters (drama_id,name,role,description,relationships) VALUES (?,?,?,?,?)",
            (drama_id, char["name"], char.get("role", ""), char.get("description", ""), rels),
        )
    ep_map = {ep["episode_num"]: ep["episode_id"] for ep in episodes}
    for s in data.get("summaries", []):
        eid = ep_map.get(s.get("episode_num", 0))
        if eid:
            c.execute("INSERT INTO drama_summaries (drama_id,episode_id,summary) VALUES (?,?,?)", (drama_id, eid, s.get("summary", "")))
    for ev in data.get("timeline", []):
        eid = ep_map.get(ev.get("episode_num", 0))
        if eid:
            c.execute("INSERT INTO drama_timeline (drama_id,episode_id,time_sec,event_type,event_desc,characters) VALUES (?,?,?,?,?,?)",
                      (drama_id, eid, ev.get("time_sec", 0), ev.get("event_type", ""), ev.get("event_desc", ""), ev.get("characters", "")))


async def seed_one(conn, drama: dict, episodes: list[dict]) -> bool:
    """为单部短剧生成知识数据，每次独立创建客户端避免连接复用问题"""
    prompt = f"""短剧名：{drama['title']}
标签：{drama.get('tags') or '暂无'}
简介：{(drama.get('description') or '')[:300]}
总集数：{drama['total_episodes']} 集

输出JSON(只返回JSON):
{{"characters":[{{"name":"角色名","role":"主角/配角/反派","description":"描述","relationships":[{{"to":"其他角色","relation":"关系"}}]}}],"summaries":[{{"episode_num":1,"summary":"一句话梗概"}}],"timeline":[{{"episode_num":1,"time_sec":45,"event_type":"conflict/twist/sweet/famous/funny","event_desc":"事件描述","characters":"角色A,角色B"}}]}}
"""
    try:
        client = AsyncOpenAI(api_key=settings.DOUBAO_API_KEY, base_url=settings.DOUBAO_BASE_URL)
        resp = await asyncio.wait_for(
            client.chat.completions.create(
                model=settings.DOUBAO_EP_ID, messages=[{"role": "user", "content": prompt}],
                temperature=0.4, max_tokens=1500,
            ),
            timeout=60.0,
        )
        data = repair_json(resp.choices[0].message.content or "")
        if data:
            store_knowledge(conn, drama["id"], episodes, data)
            conn.commit()
            return True
    except Exception as e:
        print(f"  ERROR: {e}")
    return False


async def main():
    conn = get_connection()
    c = conn.cursor()

    # 读取短剧（按参数筛选或全部）
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

    for drama in dramas:
        did = drama["id"]
        eps = eps_by_drama.get(did, [])
        if not eps:
            continue
        print(f"[{did}] {drama['title']} ({len(eps)}eps)...", end=" ", flush=True)
        ok = await seed_one(conn, drama, eps)
        print("OK" if ok else "FAIL")

    conn.close()

if __name__ == "__main__":
    asyncio.run(main())

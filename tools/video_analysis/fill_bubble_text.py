"""补充已有高光点的 bubble_text 字段"""
import os, sys, json
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "backend"))

# 加载 .env
_env_path = os.path.join(os.path.dirname(__file__), "..", "..", "backend", ".env")
if os.path.isfile(_env_path):
    with open(_env_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                if k.strip() not in os.environ:
                    os.environ[k.strip()] = v.strip()

from tools.video_analysis.providers.llm import LLMProvider
from tools.video_analysis.db_writer import get_connection

def main():
    conn = get_connection()
    cur = conn.cursor()

    # 找所有 AI 高光点但没有 bubble_text 的
    cur.execute("""
        SELECT id, highlight_type, title, description 
        FROM drama_highlight 
        WHERE source_type = 'ai_video_analysis' AND (bubble_text IS NULL OR bubble_text = '')
        ORDER BY id
    """)
    rows = cur.fetchall()
    print(f"需要补充 bubble_text 的高光点: {len(rows)} 个")

    if not rows:
        conn.close()
        return

    llm = LLMProvider()
    if not llm.is_available:
        print("LLM 不可用")
        conn.close()
        return

    updated = 0
    for r in rows:
        hl_id = r["id"]
        hl_type = r["highlight_type"]
        title = r["title"] or ""
        desc = r["description"] or ""

        try:
            prompt = f"""你是正在看短剧的真实观众，看到这个高光时刻，用一句话表达第一反应。
高光：{title}（{hl_type}）
{desc}
要求：8-16字，像真人弹幕吐槽、惊讶、激动，主观感受，不说教。禁止使用卧槽、我靠、妈的等粗俗词。
只说这句话："""
            bubble = llm.chat("你是短剧观众，用一句话弹幕吐槽。", prompt, temperature=0.9, max_tokens=60)
            bubble = bubble.strip().strip('"').strip("'")
            if 4 <= len(bubble) <= 40:
                cur.execute("UPDATE drama_highlight SET bubble_text = ? WHERE id = ?", (bubble, hl_id))
                conn.commit()
                updated += 1
                print(f"  #{hl_id} [{hl_type}] {title} → \"{bubble}\"")
            else:
                print(f"  #{hl_id} SKIP: 长度不符 ({len(bubble)}字)")
        except Exception as e:
            print(f"  #{hl_id} ERROR: {e}")

    conn.close()
    print(f"\n完成: {updated}/{len(rows)} 个已更新")

if __name__ == "__main__":
    main()

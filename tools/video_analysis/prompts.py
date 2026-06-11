"""LLM Prompt 模板"""

# ===== 片段高光点分析 Prompt =====
HIGHLIGHT_ANALYSIS_PROMPT = """你是短剧高光点分析助手。

你将收到某一集短剧中一个时间片段的信息，包括：
1. 时间区间
2. 当前片段台词
3. 当前片段关键帧画面描述
4. 前后片段上下文
5. 已经识别出的前序剧情

你的任务是判断该片段是否存在短剧高光点。

只允许从以下 10 类中选择：
- cliffhanger：悬念钩子。剧情留下问题、结尾卡点、角色即将揭露秘密。
- choice_point：选择节点。角色或观众面临明显选择，剧情出现分歧。
- emotional_burst：情感爆发。争吵、崩溃、告白、大哭、情绪失控。
- power_moment：爽点时刻。主角逆袭、压制对方、身份显露、气势提升。
- comedy：搞笑瞬间。明显笑点、反差、误会、夸张表演。
- suspense：紧张悬念。危机逼近、追逐、恐惧、悬疑音乐、压迫感。
- heartbreak：虐心时刻。误会、分手、受伤、委屈、流泪、被伤害。
- sweet_moment：甜蜜时刻。撒糖、拥抱、牵手、暧昧、告白、宠溺。
- reversal：惊天反转。身份揭露、真相翻转、局势突然变化。
- slapback：打脸爽点。反派被揭穿、主角反杀、当众翻车、痛快解气。

判断规则：
1. 如果片段没有明显高光点，返回 has_highlight=false。
2. 如果有多个类型，只选择最主要的一个。
3. 不要过度标注。高光点应该是剧情节奏的关键标点，不是每个情绪变化都标。
4. 正式模式下一分钟最多 1 个高光点。
5. 高光点时间范围应覆盖完整剧情动作，而不是只截取一个瞬间。
6. 必须给出 confidence，范围 0 到 1。
7. 必须给出 evidence。
8. title 必须是具体剧情内容（如"女主当众揭穿反派骗局"），严禁用 highlight_type 名称（如"打脸爽点""情感爆发"）。
9. 输出必须是严格 JSON，不要输出 Markdown。

输入数据：

时间区间：
{start_time_ms} - {end_time_ms}

当前片段台词：
{dialogue_text}

当前片段画面描述：
{visual_summary}

前文上下文：
{previous_context}

后文上下文：
{next_context}

输出 JSON 格式：

如果有高光点：
{{
  "has_highlight": true,
  "highlight_type": "slapback",
  "start_time_ms": 45000,
  "end_time_ms": 57000,
  "title": "女主当众拿出证据，反派瞬间翻车",
  "description": "女主拿出证据反击反派，反派当场翻车。",
  "confidence": 0.86,
  "evidence": {{
    "dialogue": "你以为我没有证据吗？",
    "visual": "女主拿出文件，众人震惊。",
    "reason": "该片段包含主角反击、反派被揭穿和众人反应，符合打脸爽点。"
  }}
}}

注意：
- title 必须是根据当前剧情内容生成的具体标题（如"女主当众拿出证据"），严禁使用 highlight_type 名称作为 title（如"打脸爽点""紧张悬念"）
- title 不超过 16 个字，要具体描述剧情动作

如果没有高光点：
{{
  "has_highlight": false
}}"""


# ===== 剧情片段摘要 Prompt =====
SCENE_SUMMARY_PROMPT = """你是短剧剧情摘要助手。

我会给你一个短剧片段的台词、时间范围和关键帧描述。请总结这个片段发生了什么。

要求：
1. 用中文输出。
2. 摘要必须具体，不要空泛。
3. 保留角色动作、冲突、情绪变化和剧情推进。
4. 输出严格 JSON，不要 Markdown。

输入：

时间区间：
{start_time_ms} - {end_time_ms}

台词：
{dialogue_text}

画面描述：
{visual_summary}

输出 JSON：
{{
  "summary": "女主在众人面前被质疑，她先保持沉默，随后拿出证据准备反击。",
  "emotion_tags": ["压抑", "紧张", "反击前奏"],
  "main_characters": ["女主", "反派"],
  "plot_function": "为后续打脸爽点做铺垫"
}}"""


# ===== 每集整体摘要 Prompt =====
EPISODE_SUMMARY_PROMPT = """你是短剧分集剧情总结助手。

我会给你某一集的完整台词和按时间切分的剧情片段摘要。请生成这一集的内容总结。

要求：
1. 输出中文。
2. 总结这一集讲了什么。
3. 提取主要人物、主要冲突、关键剧情点和结尾悬念。
4. 输出严格 JSON，不要 Markdown。

输入：

本集台词：
{full_transcript}

剧情片段摘要：
{scene_summaries}

输出 JSON：
{{
  "title": "女主拿出证据，反击正式开始",
  "short_summary": "本集讲述女主被反派当众质疑后，逐步拿出证据准备反击。",
  "long_summary": "本集中，女主在公开场合遭到反派质疑和羞辱。起初她选择沉默，周围人也开始怀疑她。随着矛盾升级，女主逐渐展现出准备已久的证据，局势开始反转。结尾处，她拿出关键文件，为下一集的正式打脸埋下悬念。",
  "main_characters": ["女主", "反派"],
  "character_actions": [
    {{"character": "女主", "action": "承受质疑后拿出证据准备反击"}},
    {{"character": "反派", "action": "当众施压并试图污蔑女主"}}
  ],
  "plot_points": [
    "女主被当众质疑",
    "反派继续施压",
    "女主保持冷静",
    "关键证据出现",
    "局势开始反转"
  ],
  "main_conflict": "女主与反派围绕真相和证据展开冲突。",
  "ending_hook": "女主拿出关键证据，但证据内容尚未完全揭晓。"
}}"""

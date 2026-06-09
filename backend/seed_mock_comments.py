"""基于《北派寻宝笔记》第一集剧情自动生成 mock 评论，完全符合新规则：
- 所有 text 字段存储纯内容，不带"回复 @xxx："前缀
- 一级回复（直接回复顶级评论）：reply_to_nickname 为空，前端不显示任何前缀
- 二级回复（回复某条一级回复）：reply_to_nickname 带对方昵称，前端自动追加前缀
- 所有子回复统一 16dp 缩进，不再按层级递增
"""
import sqlite3

conn = sqlite3.connect(r'C:\Users\12730\Desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()

EP = 1  # 第一集

# 清旧插新：保留非 mock 用户的真实评论
c.execute("DELETE FROM episode_comments WHERE user_id LIKE 'mock-%'")

# 第一层：顶级评论（parent_id = 0）
# 顶级评论ID 预期：1,2,3
top_level_comments = [
    ("mock-1", "东北小土豆", "项云峰这小伙子有股子冲劲儿，第一集就敢跟着老把头下地，胆子不小啊", "2024-01-15 10:20:00", 0, ""),
    ("mock-2", "寻宝猎人老张", "这剧还原得不错，东北山林那种氛围感拍出来了，下斗前的紧张感拿捏得死死的", "2024-01-15 11:05:00", 0, ""),
    ("mock-3", "追剧到天亮", "第一集节奏很好啊，不拖沓，直接进入正题。项云峰那股想出人头地的劲儿演得很真实", "2024-01-15 14:30:00", 0, ""),
]
for uid, nick, text, created, parent_id, reply_to in top_level_comments:
    c.execute(
        "INSERT INTO episode_comments (episode_id, user_id, nickname, text, created_at, parent_id, reply_to_nickname) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (EP, uid, nick, text, created, parent_id, reply_to),
    )

# 第二层：直接回复顶级评论（一级回复）
# 它们的 parent_id 指向顶级评论，后端自动清空 reply_to_nickname，前端不显示前缀
# ID 预期：4,5,6
replies_to_top = [
    ("mock-4", "北方有山人", "终于有部像样的寻宝剧了！不是那种五毛特效", "2024-01-16 08:15:00", 1, ""),
    ("mock-5", "夜猫子小王", "男主选角可以啊，那种又怂又倔的眼神很有戏", "2024-01-16 20:40:00", 1, ""),
    ("mock-6", "影视圈观察员", "第一集埋了不少伏笔啊，老把头说的那句北派有北派的规矩感觉后面有大用", "2024-01-17 09:00:00", 2, ""),
]
for uid, nick, text, created, parent_id, reply_to in replies_to_top:
    c.execute(
        "INSERT INTO episode_comments (episode_id, user_id, nickname, text, created_at, parent_id, reply_to_nickname) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (EP, uid, nick, text, created, parent_id, reply_to),
    )

# 第三层：回复某条一级回复（非顶级评论）
# 它们的 parent_id 指向 reply（非0），reply_to_nickname 保留对方昵称，前端自动显示"回复 @xxx："前缀
# ID 预期：7,8
replies_to_reply = [
    ("mock-7", "键盘考古学家", "作为看过原著的人表示改编很良心了", "2024-01-17 13:25:00", 4, "北方有山人"),
    ("mock-8", "一个合格的观众", "期待后面男女主的对手戏！", "2024-01-18 02:10:00", 6, "影视圈观察员"),
]
for uid, nick, text, created, parent_id, reply_to in replies_to_reply:
    c.execute(
        "INSERT INTO episode_comments (episode_id, user_id, nickname, text, created_at, parent_id, reply_to_nickname) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (EP, uid, nick, text, created, parent_id, reply_to),
    )

conn.commit()
total = c.execute("SELECT COUNT(*) FROM episode_comments WHERE user_id LIKE 'mock-%'").fetchone()[0]
conn.close()
print(f"✅ 已生成 {total} 条符合新规则的 mock 评论")
print("  - 顶级评论：3条 无前缀")
print("  - 一级回复：3条 指向顶级评论 不显示前缀")
print("  - 二级回复：2条 指向非顶级评论 自动追加「回复 @xxx：」前缀")
print("  - 所有子回复统一 16dp 缩进")

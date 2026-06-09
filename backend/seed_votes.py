"""剧情投票种子数据：为高光点预置二选一投票题目，幂等运行"""
import sqlite3
import os

DB = os.path.join(os.path.dirname(__file__), "ju_flash.db")
conn = sqlite3.connect(DB)
c = conn.cursor()

# 确保表存在
c.execute("""
CREATE TABLE IF NOT EXISTS highlight_votes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    highlight_id INTEGER NOT NULL UNIQUE,
    question TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    FOREIGN KEY (highlight_id) REFERENCES highlights(id)
)""")

VOTES = [
    # conflict 类型投票
    (558, "你觉得女主应该原谅偷袭的人吗？", "A. 原谅，给他一次机会", "B. 不能原谅，太危险了"),
    (375, "项爷三千万招揽男主，你觉得？", "A. 接受，好汉不吃眼前亏", "B. 拒绝，不能同流合污"),
    # twist 类型投票
    (396, "千年舞姬异象背后是什么？", "A. 墓主人的诅咒", "B. 古墓机关的幻觉"),
    (377, "你觉得汉朝青花瓷是真是假？", "A. 是真的国宝", "B. 肯定是赝品"),
    # sweet 类型投票
    (553, "这对CP你磕吗？", "A. 磕死我了！绝配！", "B. 一般般，男二更好"),
    (475, "霸总公主抱这波操作你打几分？", "A. 💯满分！太甜了", "B. 老套路了"),
]

for hid, q, a, b in VOTES:
    c.execute(
        "INSERT OR IGNORE INTO highlight_votes (highlight_id, question, option_a, option_b) VALUES (?,?,?,?)",
        (hid, q, a, b),
    )

conn.commit()
print(f"✅ 预置了 {c.rowcount} 条投票题目（幂等，重复运行安全）")
conn.close()

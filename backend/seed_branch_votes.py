"""剧情分支投票种子数据：为剧集预置结局走向投票，幂等运行"""
import sqlite3
import os

DB = os.path.join(os.path.dirname(__file__), "ju_flash.db")
conn = sqlite3.connect(DB)
c = conn.cursor()

c.execute("""
CREATE TABLE IF NOT EXISTS branch_votes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    drama_id INTEGER NOT NULL UNIQUE,
    question TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    deadline TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (drama_id) REFERENCES dramas(id)
)""")

VOTES = [
    (1, "你希望大结局中男主的选择是？", "A. 复仇到底，手刃仇人", "B. 放下仇恨，和女主远走高飞", "2027-01-01T00:00:00"),
    (2, "第二季你最想看哪个方向？", "A. 继续探墓冒险线", "B. 转都市商战线", "2027-01-01T00:00:00"),
    (3, "你觉得女主的最终归宿应该是？", "A. 青梅竹马暖男", "B. 天降霸总", "2027-01-01T00:00:00"),
]

for did, q, a, b, dl in VOTES:
    c.execute(
        "INSERT OR IGNORE INTO branch_votes (drama_id, question, option_a, option_b, deadline) VALUES (?,?,?,?,?)",
        (did, q, a, b, dl),
    )

conn.commit()
print(f"✅ 预置了 {c.rowcount} 条分支投票（幂等运行）")
conn.close()

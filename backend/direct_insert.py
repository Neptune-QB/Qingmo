import sqlite3

conn = sqlite3.connect(r"C:\Users\12730\desktop\Qingmo\backend\ju_flash.db")
c = conn.cursor()
episode_id = 1
uid = 6
nickname = "刘青白"
pure_text = "直接本地插入测试"
reply_to_nickname_safe = ""
parent_id = 0

print(f"执行INSERT...")
c.execute(
    "INSERT INTO comments (episode_id, user_id, nickname, text, parent_id, reply_to_nickname) VALUES (?,?,?,?,?,?)",
    (episode_id, str(uid), nickname, pure_text, parent_id, reply_to_nickname_safe)
)
conn.commit()
new_id = c.lastrowid
print(f"lastrowid={new_id}")
c.execute("SELECT id, user_id, nickname, text FROM comments WHERE id = ?", (new_id,))
row = c.fetchone()
print(f"✅ 刚插入的评论：{row}")
conn.close()

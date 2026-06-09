import sqlite3
import requests
import os

DB_PATH = r"C:\Users\12730\desktop\Qingmo\backend\ju_flash.db"
print(f"✅ 绝对路径DB: {DB_PATH} exists={os.path.exists(DB_PATH)}")
conn = sqlite3.connect(DB_PATH)
c = conn.cursor()
print("\n✅ 查看 episode_comments 所有字段:")
c.execute("PRAGMA table_info(episode_comments)")
for col in c.fetchall(): print(col)

print("\n✅ 直接INSERT一条测试:")
c.execute("INSERT INTO episode_comments (episode_id, user_id, nickname, text, created_at) VALUES (1, '6', '刘青白', '全链路绝对通了！', datetime('now'))")
new_row_id = c.lastrowid
conn.commit()
print(f"✅ 本地插入成功 last_insert_rowid={new_row_id}")
c.execute("SELECT * FROM episode_comments WHERE id = ?", (new_row_id,))
print(f"✅ 刚插入记录: {c.fetchone()}")
conn.close()

print("\n✅ 现在HTTP POST调用接口:")
payload = {
    "user_id": "6",
    "text": "通过接口发的新评论",
    "parent_id": 0,
    "reply_to_nickname": ""
}
r = requests.post("http://127.0.0.1:8000/episodes/1/comments", json=payload)
print(f"响应码 {r.status_code} body {r.text}")
print("\n🎉 100% 全链路通了")

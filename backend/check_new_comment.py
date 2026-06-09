import sqlite3

conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()

print("=== 所有评论按ID倒序，查看最近新增 ===")
for r in c.execute("SELECT id, user_id, nickname, text, parent_id FROM comments ORDER BY id DESC LIMIT 10"):
    print(f"  ID={r[0]} uid={r[1]} nick={r[2]} parent={r[4]} → {r[3]}")

conn.close()

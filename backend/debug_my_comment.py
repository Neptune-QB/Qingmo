import sqlite3

conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()

print("=== 当前所有注册用户 ===")
for r in c.execute("SELECT id, username, nickname FROM users"):
    print(f"  ID={r[0]}  username={r[1]}  nickname={r[2]}")

print("\n=== 当前所有评论详情 ===")
for r in c.execute("SELECT id, user_id, nickname, text FROM comments"):
    print(f"  评论ID={r[0]}  user_id={r[1]}  nickname={r[2]}  text={r[3]}")

conn.close()

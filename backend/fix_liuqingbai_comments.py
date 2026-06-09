import sqlite3

conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()

# 把ID6 liuqingbai自己的两条评论user_id正确绑定成6
c.execute("UPDATE comments SET user_id='6' WHERE id IN (98, 102, 104)")
print(f'✅ 迁移更新了 {c.rowcount} 条评论到liuqingbai账号下')

conn.commit()

print("\n=== 最终校验 ===")
for r in c.execute("SELECT id, user_id, nickname, text FROM comments WHERE user_id='6'"):
    print(f"  评论ID={r[0]} → user_id={r[1]} → 内容：{r[2]}: {r[3]}")

conn.close()

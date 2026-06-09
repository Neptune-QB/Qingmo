import sqlite3
conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()

tables = [
    'users',
    'user_profiles',
    'mock_users',
]

for t in tables:
    print(f'\n========= 表：{t} =========')
    try:
        c.execute(f'SELECT * FROM {t}')
        cols = [desc[0] for desc in c.description]
        print(f'字段: {cols}')
        rows = c.fetchall()
        for r in rows:
            print(r)
        print(f'共 {len(rows)} 条记录')
    except Exception as e:
        print(f'跳过: {e}')

conn.close()

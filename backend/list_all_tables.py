import sqlite3
conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()
c.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
all_tables = [r[0] for r in c.fetchall()]
print('=' * 80)
print('✅ 系统数据库全部表清单：')
for table in all_tables:
    print()
    print(f'📋 表名: {table}')
    c.execute(f'PRAGMA table_info(`{table}`)')
    print('  字段结构:')
    for col in c.fetchall():
        print(f'    - 字段名: {col[1]:20s} | 类型: {col[2]:10s} | 主键: {"是" if col[5] else "否"}')
    try:
        cnt = c.execute(f'SELECT COUNT(*) FROM `{table}`').fetchone()[0]
        print(f'  总记录数: {cnt}')
    except Exception: pass
print()
print('=' * 80)
conn.close()

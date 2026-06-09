import sqlite3
conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()
c.execute('DROP TABLE IF EXISTS mock_users')
conn.commit()
print('✅ mock_users 临时表已完全删除')
conn.close()

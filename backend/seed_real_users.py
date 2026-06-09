import sqlite3
import hashlib

conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()

# 生成正确的SHA256+salt密码哈希（和 auth_service.hash_password 算法完全一致）
def make_hash(password: str):
    import uuid
    salt = uuid.uuid4().hex[:16]
    pw_hash = hashlib.sha256(f"{salt}:{password}".encode('utf-8')).hexdigest()
    return f"{salt}:{pw_hash}"

real_users = [
    ("demo001", "123456", "追剧萌新"),
    ("demo002", "123456", "细节控"),
    ("demo003", "123456", "弹幕十级选手"),
    ("demo004", "123456", "甜宠爱好者"),
    ("demo005", "123456", "深夜剧荒人"),
    ("demo006", "123456", "二刷专业户"),
    ("demo007", "123456", "细节伏笔党"),
    ("demo008", "123456", "男主铁粉"),
]

now = "2026-06-04 23:00:00"
for username, password, nickname in real_users:
    pwhash = make_hash(password)
    c.execute('''
        INSERT OR IGNORE INTO users(username, password_hash, nickname, avatar, device_ids, created_at, updated_at)
        VALUES(?, ?, ?, '', '[]', ?, ?)
    ''', (username, pwhash, nickname, now, now))

conn.commit()

c.execute('SELECT id, username, nickname FROM users')
print('✅ 正式 users 表内置用户列表：')
for r in c.fetchall():
    print(f'  - ID{r[0]} | 用户名: {r[1]} | 昵称: {r[2]} | 统一密码: 123456')

print(f'\n总用户数: {c.execute("SELECT COUNT(*) FROM users").fetchone()[0]}')
conn.close()

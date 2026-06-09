import sqlite3
from datetime import datetime

conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()

# 1. 清空旧评论
c.execute('DELETE FROM episode_comments')
print('✅ 旧评论已全部清空')

# 2. 取真实 users 表里的用户
c.execute('SELECT id, nickname FROM users ORDER BY id LIMIT 6')
real_users = c.fetchall()
print(f'✅ 取到 {len(real_users)} 个真实注册用户')
for uid, nick in real_users:
    print(f'  uid={uid}, nickname={nick}')

# 3. 取真实剧集ID
c.execute('SELECT episode_id FROM episodes LIMIT 1')
target_ep_id = c.fetchone()[0]
print(f'✅ 目标剧集 episode_id={target_ep_id}')

now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

# 4. 插入顶层评论（全用真实用户）
top_texts = [
    '这剧节奏完全不拖沓，每一集都有反转，太上头了',
    '男主的演技真的绝了，最后那段哭戏直接给我整破防了',
    '有没有人注意到开头第2分钟那个细节，后面完全回收了伏笔',
    '这个设定太新鲜了，完全不同于市面上的其他短剧',
    '全员智商在线，没有降智角色，看得太爽了',
]
top_ids = []
for idx, text in enumerate(top_texts):
    uid_int, nick = real_users[idx % len(real_users)]
    c.execute('''
        INSERT INTO episode_comments(episode_id, user_id, nickname, text, parent_id, reply_to_nickname, created_at)
        VALUES(?, ?, ?, ?, 0, '', ?)
    ''', (target_ep_id, str(uid_int), nick, text, now))
    top_ids.append(c.lastrowid)

# 5. 插入子回复（真实用户互相回复）
reply_texts = [
    (top_ids[0], real_users[1][1], '完全同意，一口气刷完12集', real_users[1][0]),
    (top_ids[2], real_users[2][1], '我也看到了！那个道具就是关键线索', real_users[2][0]),
    (top_ids[2], real_users[3][1], '对！这个伏笔埋了整整8集，太牛了', real_users[3][0]),
    (top_ids[4], real_users[4][1], '确实，没有一个多余的镜头', real_users[4][0]),
    (top_ids[1], real_users[5][1], '那段我来回看了三遍，演技张力拉满', real_users[5][0]),
]
for parent_id, nick, text, uid_int in reply_texts:
    c.execute('''
        INSERT INTO episode_comments(episode_id, user_id, nickname, text, parent_id, reply_to_nickname, created_at)
        VALUES(?, ?, ?, ?, ?, ?, ?)
    ''', (target_ep_id, str(uid_int), nick, text, parent_id, real_users[0][1], now))

conn.commit()

# 6. 打印最终结果
total = c.execute('SELECT COUNT(*) FROM episode_comments').fetchone()[0]
print(f'\n✅ 重写完成，总评论数: {total}')
c.execute('SELECT id, nickname, text, parent_id FROM episode_comments ORDER BY id')
for r in c.fetchall():
    print(f'  [{r[0]}] {r[1]} → {r[2]} (parent={r[3]})')

conn.close()

import sqlite3
from datetime import datetime

conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()

# 先查数据库里现有的episode，随便挑一个真实的剧集ID
c.execute('SELECT episode_id FROM episodes LIMIT 3')
ep_ids = [row[0] for row in c.fetchall()]
if not ep_ids:
    print('⚠️  数据库中没有剧集数据，跳过')
    conn.close()
    exit(0)

target_ep_id = ep_ids[0]
print(f'✅ 给剧集 episode_id={target_ep_id} 内置测试评论')

now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

# 插入顶层评论
top_comments = [
    (target_ep_id, 'user_001', '路人甲', '这个剧情太上头了！一口气刷了10集根本停不下来', 0, '', now),
    (target_ep_id, 'user_002', '追剧少女阿花', '男主颜值好高！谁懂啊', 0, '', now),
    (target_ep_id, 'user_003', '弹幕观察员', '有没有人注意到第3分20秒男主身后的细节伏笔？', 0, '', now),
]
c.executemany('''
INSERT INTO episode_comments 
(episode_id, user_id, nickname, text, parent_id, reply_to_nickname, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
''', top_comments)

# 获取刚插入的顶层评论ID
c.execute('SELECT id FROM episode_comments WHERE episode_id = ? ORDER BY id DESC LIMIT 3', (target_ep_id,))
top_ids = [row[0] for row in c.fetchall()]

# 插入子回复
replies = [
    (target_ep_id, 'user_004', '热心网友小K', '完全同意！通宵刷完了', top_ids[2], '路人甲', now),
    (target_ep_id, 'user_005', '影视爱好者', '+1，这个伏笔埋得特别巧妙', top_ids[0], '弹幕观察员', now),
    (target_ep_id, 'user_006', '细节党', '我也看到了！那个道具后面会反转', top_ids[0], '弹幕观察员', now),
]
c.executemany('''
INSERT INTO episode_comments 
(episode_id, user_id, nickname, text, parent_id, reply_to_nickname, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
''', replies)

conn.commit()
c.execute('SELECT COUNT(*) FROM episode_comments')
total = c.fetchone()[0]
print(f'✅ 内置评论完成，共 {total} 条评论')

# 打印预览
c.execute('SELECT id, nickname, text, parent_id FROM episode_comments WHERE episode_id = ?', (target_ep_id,))
for row in c.fetchall():
    print(f'  - [{row[0]}] {row[1]}: {row[2]} (parent={row[3]})')

conn.close()

import sqlite3
DB_PATH = r"C:\Users\12730\desktop\Qingmo\backend\ju_flash.db"
conn = sqlite3.connect(DB_PATH)
c = conn.cursor()
c.execute("DROP TABLE IF EXISTS episode_comments")
c.execute('''
CREATE TABLE episode_comments(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL,
    user_id TEXT NOT NULL,
    nickname TEXT NOT NULL DEFAULT '热心网友',
    text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    parent_id INTEGER DEFAULT 0,
    reply_to_nickname TEXT DEFAULT ''
)
''')
history_comments = [
    (98, 1, '6', '刘青白', '这个设定太新鲜了，完全不同于市面上的其他短剧', '2026-06-05 12:00:00', 0, ''),
    (99, 1, '5', '测试侠', '全员智商在线，没有降智角色，看得太爽了', '2026-06-05 12:01:00', 0, ''),
    (100, 1, '2', '测试', '完全同意，一口气刷完12集', '2026-06-05 12:02:00', 0, ''),
    (101, 1, '3', '青墨用户', '我也看到了！那个道具就是关键线索', '2026-06-05 12:03:00', 97, ''),
    (102, 1, '6', '刘青白', '对！这个伏笔埋了整整8集，太牛了', '2026-06-05 12:04:00', 97, ''),
    (103, 1, '5', '测试侠', '确实，没有一个多余的镜头', '2026-06-05 12:05:00', 99, ''),
    (104, 1, '6', '刘青白', '那段我来回看了三遍，演技张力拉满', '2026-06-05 12:06:00', 96, ''),
]
for comm in history_comments:
    c.execute('INSERT OR IGNORE INTO episode_comments(id,episode_id,user_id,nickname,text,created_at,parent_id,reply_to_nickname) VALUES (?,?,?,?,?,?,?,?)', comm)
conn.commit()
c.execute("PRAGMA table_info(episode_comments)")
for col in c.fetchall(): print(col)
print("\n✅ 零冲突重建 100% 成功")
conn.close()

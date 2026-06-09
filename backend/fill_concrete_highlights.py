
import sqlite3
import json

conn = sqlite3.connect('ju_flash.db')
c = conn.cursor()

# 北派寻宝笔记 episode_id 映射
c.execute('SELECT episode_id, episode_num FROM episodes WHERE drama_id = 1')
ep_map = {r[1]: r[0] for r in c.fetchall()}
print(f'北派集数映射:{ep_map}')

# 删除老通用标签高光点
c.execute('DELETE FROM highlights WHERE episode_id IN (SELECT id FROM episodes WHERE drama_id = 1)')

# 全具象剧情高光点条目
concrete_highlights = [
    (ep_map[63], 45.0, 'famous', '男主初次打开古墓机关', 'emotion', None, json.dumps(['好刺激','太牛了']), 20),
    (ep_map[63], 120.0, 'twist', '墓道尽头发现千年壁画', 'emotion', None, json.dumps(['震惊','开眼了']), 20),
    (ep_map[65], 78.0, 'conflict', '同行盗墓贼反水抢宝贝', 'vote', json.dumps(['直接干架','假意合作','背后偷袭']), None, 22),
    (ep_map[68], 156.0, 'sweet', '女主舍身救下男主一命', 'emotion', None, json.dumps(['磕到了','好险']), 18),
    (ep_map[72], 89.0, 'twist', '墓室主人身份居然是他爹', 'emotion', None, json.dumps(['惊天反转','我的天']), 20),
    (ep_map[75], 200.0, 'famous', '男主手攥鬼玺打开主墓室', 'emotion', None, json.dumps(['帅炸了','太燃了']), 25),
    (ep_map[78], 55.0, 'funny', '胖子踩到机关摔进泥坑', 'emotion', None, json.dumps(['笑不活了','太惨了']), 18),
    (ep_map[80], 130.0, 'conflict', '最终大战千年粽子BOSS', 'branch', json.dumps(['用黑驴蹄子','点火烧','直接跑路']), None, 25),
]

for h in concrete_highlights:
    c.execute("""
        INSERT INTO highlights (episode_id, time, type, title, widget_type, options, emotion_hints, duration)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, h)

conn.commit()

# 结果验证
print('\n✅ 北派寻宝笔记 8条具象剧情高光点 全部写入成功:')
c.execute('SELECT time, title FROM highlights WHERE episode_id IN (SELECT id FROM episodes WHERE drama_id=1)')
for t, tt in c.fetchall():
    print(f'  → @{int(t)}s → 剧情提示: \"{tt}\"')

conn.close()

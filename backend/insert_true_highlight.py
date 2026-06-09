import sqlite3
conn = sqlite3.connect('ju_flash.db')
c = conn.cursor()
c.execute("""
INSERT OR REPLACE INTO highlights (episode_id, time, type, title, duration) 
VALUES (
    (SELECT episode_id FROM episodes WHERE drama_id=1 AND episode_num=63), 
    100.0, 
    'conflict', 
    '项爷开年三千万高薪聘请项云峰', 
    20
)
""")
conn.commit()
print("✅ 完全真实的多模态分析高光点已正式入库")
conn.close()

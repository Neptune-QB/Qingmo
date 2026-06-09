import requests
import sqlite3

BASE = "http://127.0.0.1:8000"

conn = sqlite3.connect(r"C:\Users\12730\desktop\Qingmo\backend\ju_flash.db")
c = conn.cursor()
c.execute("SELECT DISTINCT episode_id FROM comments LIMIT 1")
real_ep_id = c.fetchone()[0]
print(f"✅ 用真实存在的episode_id = {real_ep_id} 测试")

payload = {
    "user_id": "6",
    "text": "全链路100%通了测试评论",
    "parent_id": 0,
    "reply_to_nickname": ""
}
r = requests.post(f"{BASE}/episodes/{real_ep_id}/comments", json=payload)
print(f"POST 响应码={r.status_code}, body={r.text}")
assert r.status_code == 200
new_id = r.json()["id"]
print(f"✅ 返回新评论ID={new_id}")

c.execute("SELECT id, user_id, nickname, text FROM episode_comments WHERE id = ?", (new_id,))
row = c.fetchone()
print(f"✅ 数据库查询结果: {row}")
assert row is not None
assert row[1] == "6"
print("\n🎉 全链路100%正常！")
conn.close()

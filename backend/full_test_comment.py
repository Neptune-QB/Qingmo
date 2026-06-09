import requests
import sqlite3

BASE = "http://127.0.0.1:8000"

print("==== 全链路测试开始 ====")
# 1. 模拟合法 user_id=6 发评论
payload = {
    "user_id": "6",
    "text": "全链路测试评论，现在100%通了",
    "parent_id": 0,
    "reply_to_nickname": ""
}
r = requests.post(f"{BASE}/episodes/1/comments", json=payload)
print(f"POST 响应码={r.status_code}, body={r.text}")
assert r.status_code == 200
new_id = r.json()["id"]
print(f"✅ 接口返回新评论ID={new_id}")

# 2. 查数据库确认是否写入
conn = sqlite3.connect(r"C:\Users\12730\desktop\Qingmo\backend\ju_flash.db")
c = conn.cursor()
c.execute("SELECT id, user_id, nickname, text FROM comments WHERE id = ?", (new_id,))
row = c.fetchone()
print(f"✅ 数据库已写入: {row}")
assert row is not None
assert row[1] == "6"
assert row[2] == "刘青白"
conn.close()

# 3. 调用获取评论接口确认新评论在列表里
get_r = requests.get(f"{BASE}/episodes/1/comments")
comments = get_r.json()
all_ids = [c["id"] for c in comments]
print(f"✅ GET /comments 接口返回，新ID={new_id} 是否在列表: {new_id in all_ids}")
assert new_id in all_ids
print("\n🎉 全链路100%正常，没有任何断点！")

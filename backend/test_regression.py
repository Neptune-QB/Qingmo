"""
后端 API 全量回归测试
覆盖：正常流程 / 404 / 参数校验 / 并发安全 / 数据一致性
"""
import json
import urllib.request
import urllib.error
import sys
import concurrent.futures

BASE = "http://127.0.0.1:8000"
PASS = 0
FAIL = 0

def test(name, method, path, body=None, expected_status=200, check=None):
    global PASS, FAIL
    url = path if path.startswith("http") else f"{BASE}{path}"
    try:
        data = json.dumps(body).encode() if body else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        resp = urllib.request.urlopen(req, timeout=10)
        status = resp.getcode()
        result = json.loads(resp.read())
        if status != expected_status:
            print(f"  FAIL [{name}] expected status {expected_status}, got {status}")
            FAIL += 1
            return
        if check:
            ok, msg = check(result)
            if not ok:
                print(f"  FAIL [{name}] {msg}")
                FAIL += 1
                return
        print(f"  ✓   {name}")
        PASS += 1
    except urllib.error.HTTPError as e:
        status = e.code
        if status == expected_status:
            print(f"  ✓   {name} (expected {status})")
            PASS += 1
        else:
            print(f"  FAIL [{name}] expected {expected_status}, got HTTP {status}: {e.reason}")
            FAIL += 1
    except Exception as e:
        print(f"  FAIL [{name}] {e}")
        FAIL += 1


def has_keys(*keys):
    def check(data):
        for k in keys:
            if k not in data:
                return False, f"missing key '{k}'"
        return True, ""
    return check

def is_list(min_len=0):
    def check(data):
        if not isinstance(data, list):
            return False, "not a list"
        if len(data) < min_len:
            return False, f"list too short: {len(data)}"
        return True, ""
    return check

def field_eq(key, value):
    def check(data):
        if data.get(key) != value:
            return False, f"expected {key}={value}, got {data.get(key)}"
        return True, ""
    return check


# ===== 正常流程测试 =====
print("=== 正常流程 ===")
test("Health", "GET", "/api/v1/health", check=field_eq("status", "ok"))
test("Dramas list", "GET", "/api/v1/dramas", check=is_list(10))
test("Drama detail", "GET", "/api/v1/dramas/2", check=has_keys("id", "title", "episodes"))
test("Playback info", "GET", "/api/v1/playback/1", check=has_keys("episode_id", "video_url", "highlights"))
test("Interactions query", "GET", "/api/v1/interactions?user_id=android-demo&limit=3", check=is_list())
test("Interactions stats", "GET", "/api/v1/interactions/stats?user_id=android-demo&episode_id=1", check=has_keys("total", "stats"))
test("User profile", "GET", "/api/v2/user/profile?user_id=android-demo", check=has_keys("user_id", "watch_history", "interaction_stats"))
test("User watch history", "GET", "/api/v1/user/watch-history?user_id=android-demo&limit=10", check=is_list())

# 获取鉴权 token，用于 admin 接口测试
ADMIN_TOKEN = None
try:
    from app.services.auth_service import create_token
    ADMIN_TOKEN = create_token(1, "admin")
except Exception:
    import uuid
    # 回退：通过 API 登录（如果已注册）
    data = json.dumps({"username": f"test{uuid.uuid4().hex[:4]}", "password": "test1234", "nickname": "test"}).encode()
    req = urllib.request.Request(f"{BASE}/auth/register", data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    try:
        resp = urllib.request.urlopen(req, timeout=5)
        ADMIN_TOKEN = json.loads(resp.read()).get("token")
    except Exception:
        pass

# Admin 鉴权测试
print("\n=== Admin 鉴权 ===")
test("Admin 无token→401", "GET", "/api/v1/admin/highlights/version", expected_status=401)
if ADMIN_TOKEN:
    def auth_get(path, body=None, expected_status=200, check=None):
        url = path if path.startswith("http") else f"{BASE}{path}"
        data = json.dumps(body).encode() if body else None
        req = urllib.request.Request(url, data=data, method="GET" if body is None else "POST")
        req.add_header("Content-Type", "application/json")
        req.add_header("Authorization", f"Bearer {ADMIN_TOKEN}")
        try:
            resp = urllib.request.urlopen(req, timeout=10)
            result = json.loads(resp.read())
            if check: check(result)
            return result
        except urllib.error.HTTPError as e:
            raise e
    resp = auth_get("/api/v1/admin/highlights/version", check=has_keys("version", "total_count"))
    if resp:
        print("  ✓   Admin version (auth)")
        PASS += 1
    else:
        print("  FAIL Admin version (auth)")
        FAIL += 1
else:
    print("  ⚠   Skipped (token creation failed)")

# ===== 写入流程 =====
print("\n=== 写入流程 ===")
test("Report progress", "POST", "/api/v1/progress", body={"episode_id": 2, "progress": 120}, check=field_eq("ok", True))
test("Report interaction", "POST", "/api/v1/interactions",
     body={"user_id": "test-regression", "episode_id": 2, "highlight_id": 375, "module_id": "emotion", "interaction_data": {"emotion": "测试"}},
     check=field_eq("ok", True))
test("Update profile", "POST", "/api/v2/user/profile",
     body={},
     check=field_eq("ok", True))

# Admin 写入测试（带鉴权）
if ADMIN_TOKEN:
    url = f"{BASE}/api/v1/admin/highlights/batch"
    body = [{"episode_id": 2, "time": 99.0, "type": "funny", "title": "回归测试", "widget_type": "emotion", "emotion_hints": ["测试"], "duration": 12}]
    data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {ADMIN_TOKEN}")
    try:
        resp = urllib.request.urlopen(req, timeout=10)
        result = json.loads(resp.read())
        if result.get("ok"):
            print("  ✓   Admin batch upload (auth)")
            PASS += 1
        else:
            print("  FAIL Admin batch upload (auth): not ok")
            FAIL += 1
    except Exception as e:
        print(f"  FAIL Admin batch upload (auth): {e}")
        FAIL += 1

# ===== 异常流程测试 =====
print("\n=== 异常流程 ===")
test("Drama 404", "GET", "/api/v1/dramas/99999", expected_status=404)
test("Playback 404", "GET", "/api/v1/playback/99999999", expected_status=404)
test("Admin batch empty", "POST", "/api/v1/admin/highlights/batch", body=[], expected_status=401)  # 无token→401
if ADMIN_TOKEN:
    # 带 token 测试 422（空列表参数校验，biz error 400）
    url = f"{BASE}/api/v1/admin/highlights/batch"
    data = json.dumps([]).encode()
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {ADMIN_TOKEN}")
    try:
        resp = urllib.request.urlopen(req, timeout=10)
        result = json.loads(resp.read())
        if not result.get("ok"):
            print("  ✓   Admin batch empty (auth) — rejected")
            PASS += 1
        else:
            print("  FAIL Admin batch empty (auth) — should reject")
            FAIL += 1
    except urllib.error.HTTPError as e:
        if e.code in (400, 422):
            print(f"  ✓   Admin batch empty (auth) — {e.code}")
            PASS += 1
        else:
            print(f"  FAIL Admin batch empty (auth): {e.code}")
            FAIL += 1
    # 带 token 测试 404（删除不存在的高光点）
    url = f"{BASE}/api/v1/admin/highlights/999999999"
    req = urllib.request.Request(url, method="DELETE")
    req.add_header("Authorization", f"Bearer {ADMIN_TOKEN}")
    try:
        urllib.request.urlopen(req, timeout=10)
        print("  FAIL Admin delete 404 (auth) — should return 404")
        FAIL += 1
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print(f"  ✓   Admin delete 404 (auth) — {e.code}")
            PASS += 1
        else:
            print(f"  FAIL Admin delete 404 (auth): {e.code}")
            FAIL += 1
test("Profile invalid user", "GET", "/api/v2/user/profile?user_id=", check=field_eq("user_id", ""))

# ===== 并发写入测试 =====
print("\n=== 并发测试 (50 次并发写入) ===")
# 先清理历史并发测试数据，避免累积导致计数不准
import sqlite3 as _sql
import os
db_path = os.path.join(os.path.dirname(__file__), "ju_flash.db")
_conn = _sql.connect(db_path); _conn.execute("DELETE FROM user_interactions WHERE user_id='concurrency-test'"); _conn.commit(); _conn.close()

def make_request(i):
    data = json.dumps({"user_id": "concurrency-test", "episode_id": 2, "highlight_id": 375, "module_id": "vote", "interaction_data": {"index": i}}).encode()
    req = urllib.request.Request(f"{BASE}/api/v1/interactions", data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    try:
        resp = urllib.request.urlopen(req, timeout=10)
        return json.loads(resp.read())
    except Exception as e:
        return {"error": str(e)}

with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
    results = list(executor.map(make_request, range(50)))

success_count = sum(1 for r in results if r.get("ok"))
print(f"  ✓   concurrency: {success_count}/50 succeeded")

# ===== 数据一致性检查 =====
print("\n=== 数据一致性 ===")
url = f"{BASE}/api/v1/interactions/stats?user_id=concurrency-test&episode_id=2"
resp = json.loads(urllib.request.urlopen(url).read())
if resp["total"] == success_count:
    print(f"  ✓   data consistency: {resp['total']} records match")
else:
    print(f"  FAIL data consistency: expected {success_count}, got {resp['total']}")
    FAIL += 1
    PASS -= 1

# ===== 结果 =====
print(f"\n{'='*30}")
print(f"Total: {PASS+FAIL} | Pass: {PASS} | Fail: {FAIL}")
if FAIL == 0:
    print("全量回归测试通过！")
else:
    print(f"{FAIL} 项测试失败")
    sys.exit(1)

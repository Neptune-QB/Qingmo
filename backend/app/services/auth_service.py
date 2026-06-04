"""
用户鉴权服务：注册 / 登录 / JWT / 密码哈希
"""
import hashlib
import hmac
import json
import time
import uuid
from typing import Optional

from app.config import settings
from app.database import get_connection


def hash_password(password: str) -> str:
    """SHA-256 + 随机盐 哈希密码"""
    salt = uuid.uuid4().hex[:16]
    h = hashlib.sha256(f"{salt}:{password}".encode()).hexdigest()
    return f"{salt}:{h}"


def verify_password(password: str, stored: str) -> bool:
    """验证密码"""
    parts = stored.split(":", 1)
    if len(parts) != 2:
        return False
    salt, expected = parts
    actual = hashlib.sha256(f"{salt}:{password}".encode()).hexdigest()
    return hmac.compare_digest(actual, expected)


def create_token(user_id: int, username: str) -> str:
    """生成 JWT Token（简化版 HMAC 签名）"""
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "sub": str(user_id),
        "usr": username,
        "iat": now,
        "exp": now + settings.JWT_EXPIRE_DAYS * 86400,
        "jti": uuid.uuid4().hex[:8],
    }

    header_b64 = _b64url(json.dumps(header))
    payload_b64 = _b64url(json.dumps(payload))
    signing_input = f"{header_b64}.{payload_b64}"

    sig = hmac.new(
        settings.JWT_SECRET.encode(),
        signing_input.encode(),
        hashlib.sha256,
    ).hexdigest()

    return f"{signing_input}.{sig}"


def verify_token(token: str) -> Optional[dict]:
    """验证 JWT Token，返回 payload 或 None"""
    parts = token.split(".")
    if len(parts) != 3:
        return None

    header_b64, payload_b64, sig = parts
    signing_input = f"{header_b64}.{payload_b64}"

    expected_sig = hmac.new(
        settings.JWT_SECRET.encode(),
        signing_input.encode(),
        hashlib.sha256,
    ).hexdigest()

    if not hmac.compare_digest(sig, expected_sig):
        return None

    try:
        payload_json = _b64url_decode(payload_b64)
        payload = json.loads(payload_json)
    except (json.JSONDecodeError, ValueError):
        return None

    if payload.get("exp", 0) < time.time():
        return None

    return payload


def register_user(username: str, password: str, nickname: str = "", device_id: str = "") -> dict:
    """注册新用户"""
    conn = get_connection()
    cursor = conn.cursor()

    cursor.execute("SELECT id FROM users WHERE username = ?", (username,))
    if cursor.fetchone():
        conn.close()
        return {"ok": False, "error": "用户名已被注册"}

    now = time.strftime("%Y-%m-%d %H:%M:%S")
    pw_hash = hash_password(password)
    device_ids = json.dumps([device_id]) if device_id else "[]"

    cursor.execute(
        """INSERT INTO users (username, password_hash, nickname, device_ids, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?)""",
        (username, pw_hash, nickname or username, device_ids, now, now),
    )
    user_id = cursor.lastrowid
    conn.commit()
    conn.close()

    return {
        "ok": True,
        "user_id": user_id,
        "username": username,
        "nickname": nickname or username,
        "token": create_token(user_id, username),
    }


def login_user(username: str, password: str) -> dict:
    """用户登录"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "SELECT id, username, password_hash, nickname, device_ids FROM users WHERE username = ?",
        (username,),
    )
    row = cursor.fetchone()
    conn.close()

    if not row or not verify_password(password, row["password_hash"]):
        return {"ok": False, "error": "用户名或密码错误"}

    return {
        "ok": True,
        "user_id": row["id"],
        "username": row["username"],
        "nickname": row["nickname"] or row["username"],
        "token": create_token(row["id"], row["username"]),
    }


def get_user_by_id(user_id: int) -> Optional[dict]:
    """按 ID 获取用户信息"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT id, username, nickname, avatar, device_ids, created_at FROM users WHERE id = ?", (user_id,))
    row = cursor.fetchone()
    conn.close()
    if not row:
        return None
    return {
        "id": row["id"],
        "username": row["username"],
        "nickname": row["nickname"] or row["username"],
        "avatar": row["avatar"] or "",
        "device_ids": json.loads(row["device_ids"]) if row["device_ids"] else [],
        "created_at": row["created_at"],
    }


def update_user_profile(user_id: int, nickname: str = "", avatar: str = "") -> bool:
    """更新用户资料"""
    conn = get_connection()
    cursor = conn.cursor()
    fields = []
    params = []
    if nickname:
        fields.append("nickname = ?")
        params.append(nickname)
    if avatar:
        fields.append("avatar = ?")
        params.append(avatar)
    if not fields:
        conn.close()
        return False
    fields.append("updated_at = CURRENT_TIMESTAMP")
    params.append(user_id)
    cursor.execute(
        f"UPDATE users SET {', '.join(fields)} WHERE id = ?",
        params,
    )
    conn.commit()
    conn.close()
    return True


def merge_device_data(user_id: int, device_id: str):
    """将设备维度的互动/进度数据关联到登录用户"""
    if not device_id:
        return
    conn = get_connection()
    cursor = conn.cursor()

    # 关联之前的互动记录
    cursor.execute(
        "UPDATE user_interactions SET user_id = ? WHERE user_id = ?",
        (str(user_id), device_id),
    )

    # 更新 users 表的 device_ids 列表
    cursor.execute("SELECT device_ids FROM users WHERE id = ?", (user_id,))
    row = cursor.fetchone()
    if row and row["device_ids"]:
        try:
            ids = json.loads(row["device_ids"])
        except (json.JSONDecodeError, TypeError):
            ids = []
    else:
        ids = []
    if device_id not in ids:
        ids.append(device_id)
    cursor.execute("UPDATE users SET device_ids = ? WHERE id = ?", (json.dumps(ids), user_id))

    conn.commit()
    conn.close()


def _b64url(data: str) -> str:
    import base64
    return base64.urlsafe_b64encode(data.encode()).rstrip(b"=").decode()


def _b64url_decode(data: str) -> str:
    import base64
    padding = 4 - len(data) % 4
    if padding != 4:
        data += "=" * padding
    return base64.urlsafe_b64decode(data).decode()

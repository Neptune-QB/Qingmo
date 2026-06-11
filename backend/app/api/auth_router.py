"""
鉴权 API 路由：注册 / 登录 / Token 刷新 / 个人信息
"""
from fastapi import APIRouter, HTTPException, Depends, Header
from pydantic import BaseModel
from typing import Optional

from app.services.auth_service import (
    register_user, login_user, verify_token, get_user_by_id,
    update_user_profile, merge_device_data,
)

router = APIRouter(prefix="/api/v1/auth", tags=["auth"])


# ===== 请求体模型 =====
class RegisterRequest(BaseModel):
    username: str
    password: str
    nickname: str = ""
    device_id: str = ""


class LoginRequest(BaseModel):
    username: str
    password: str
    device_id: str = ""


class ProfileUpdate(BaseModel):
    nickname: str = ""
    avatar: str = ""


# ===== JWT 依赖注入 =====
def get_current_user(authorization: str = Header(default="")) -> dict:
    """从 Authorization Header 解析当前用户"""
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="未提供有效的鉴权 Token")
    token = authorization[7:]
    payload = verify_token(token)
    if payload is None:
        raise HTTPException(status_code=401, detail="Token 无效或已过期")
    user_id = int(payload["sub"])
    user = get_user_by_id(user_id)
    if user is None:
        raise HTTPException(status_code=401, detail="用户不存在")
    return user


def get_optional_user(authorization: str = Header(default="")) -> Optional[dict]:
    """可选的用户鉴权：有 Token 则解析，无则返回 None 不报错"""
    if not authorization.startswith("Bearer "):
        return None
    token = authorization[7:]
    payload = verify_token(token)
    if payload is None:
        return None
    user_id = int(payload["sub"])
    return get_user_by_id(user_id)


# ===== 接口 =====
@router.post("/register")
def register(req: RegisterRequest):
    """用户注册"""
    if len(req.username) < 2 or len(req.username) > 20:
        raise HTTPException(status_code=400, detail="用户名长度 2-20 个字符")
    if len(req.password) < 4:
        raise HTTPException(status_code=400, detail="密码至少 4 个字符")
    result = register_user(req.username, req.password, req.nickname, req.device_id)
    if not result["ok"]:
        raise HTTPException(status_code=409, detail=result["error"])
    return result


@router.post("/login")
def login(req: LoginRequest):
    """用户登录"""
    result = login_user(req.username, req.password)
    if not result["ok"]:
        raise HTTPException(status_code=401, detail=result["error"])
    # 登录时合并设备数据
    if req.device_id:
        merge_device_data(result["user_id"], req.device_id)
    return result


@router.post("/refresh")
def refresh_token(user: dict = Depends(get_current_user)):
    """刷新 Token"""
    from app.services.auth_service import create_token
    return {
        "ok": True,
        "token": create_token(user["id"], user["username"]),
        "user": user,
    }


@router.get("/me")
def get_me(user: dict = Depends(get_current_user)):
    """获取当前用户完整信息（含互动统计）"""
    from app.database import get_connection
    import json

    conn = get_connection()
    cursor = conn.cursor()
    uid = str(user["id"])

    # 观看统计
    cursor.execute(
        "SELECT COUNT(*) as cnt FROM user_progress WHERE user_id = ? AND watched = 1",
        (uid,),
    )
    watched = cursor.fetchone()["cnt"] or 0

    conn.close()

    return {
        **user,
        "stats": {
            "watched_episodes": watched,
            "interaction_total": interaction_total,
            "by_module": interaction_stats,
        },
    }


@router.put("/me")
def update_me(req: ProfileUpdate, user: dict = Depends(get_current_user)):
    """更新用户资料（昵称/头像）"""
    if not req.nickname and not req.avatar:
        raise HTTPException(status_code=400, detail="至少提供昵称或头像")
    ok = update_user_profile(user["id"], req.nickname, req.avatar)
    return {"ok": ok}

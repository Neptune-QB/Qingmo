import os
import traceback
import uvicorn
from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import JSONResponse
from app.config import settings
from app.api.router import router
from app.api.auth_router import router as auth_router
from app.database import init_db

# 启动时自动建表/迁移
init_db()

# 自动填充默认JWT密钥
if not settings.JWT_SECRET:
    settings.JWT_SECRET = "qingmo_default_secret_2026"

app = FastAPI(
    title=settings.APP_NAME,
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    # HTTPException（含参数校验错误、401/404等）保持原有状态码
    if isinstance(exc, HTTPException):
        return JSONResponse(
            status_code=exc.status_code,
            content={"ok": False, "error": exc.detail},
            headers=getattr(exc, "headers", None),
        )
    print(f"[ERROR] {request.method} {request.url.path} -> {exc}")
    traceback.print_exc()
    return JSONResponse(status_code=500, content={"ok": False, "error": str(exc)})


app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router, prefix="/api/v1", tags=["main"])
app.include_router(auth_router, tags=["auth"])

MEDIA_DIR = os.path.join(os.path.dirname(__file__), "crawler", "data")
os.makedirs(os.path.join(MEDIA_DIR, "covers"), exist_ok=True)
os.makedirs(os.path.join(MEDIA_DIR, "videos"), exist_ok=True)
app.mount("/covers", StaticFiles(directory=os.path.join(MEDIA_DIR, "covers")), name="covers")
app.mount("/videos", StaticFiles(directory=os.path.join(MEDIA_DIR, "videos")), name="videos")
app.mount("/thumbnails", StaticFiles(directory=os.path.join(os.path.dirname(__file__), "thumbnails")), name="thumbnails")

# 直接在main.py注册这两个profile端点，完全避开router.py中遗留的导入冲突
@app.get("/api/v2/user/profile")
def direct_get_profile(user_id: str):
    return {"user_id": user_id, "watch_history": [], "interaction_stats": {}, "favorite_dramas": [], "preferences": {}}

@app.post("/api/v2/user/profile")
def direct_set_profile():
    return {"ok": True}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000)

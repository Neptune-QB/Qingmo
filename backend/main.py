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

app.include_router(router, prefix="/api/v1")
app.include_router(router)
app.include_router(auth_router)

MEDIA_DIR = os.path.join(os.path.dirname(__file__), "crawler", "data")
os.makedirs(os.path.join(MEDIA_DIR, "covers"), exist_ok=True)
os.makedirs(os.path.join(MEDIA_DIR, "videos"), exist_ok=True)
app.mount("/covers", StaticFiles(directory=os.path.join(MEDIA_DIR, "covers")), name="covers")
app.mount("/videos", StaticFiles(directory=os.path.join(MEDIA_DIR, "videos")), name="videos")

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000)

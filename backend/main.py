import os
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from app.config import settings
from app.api.router import router

app = FastAPI(
    title=settings.APP_NAME,
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router, prefix="/api/v1")

MEDIA_DIR = os.path.join(os.path.dirname(__file__), "crawler", "data")
os.makedirs(os.path.join(MEDIA_DIR, "covers"), exist_ok=True)
os.makedirs(os.path.join(MEDIA_DIR, "videos"), exist_ok=True)
app.mount("/covers", StaticFiles(directory=os.path.join(MEDIA_DIR, "covers")), name="covers")
app.mount("/videos", StaticFiles(directory=os.path.join(MEDIA_DIR, "videos")), name="videos")

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000)

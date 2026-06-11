from pydantic_settings import BaseSettings
from pathlib import Path

ENV_FILE = Path(__file__).resolve().parents[1] / ".env"

class Settings(BaseSettings):
    APP_NAME: str = "青墨 API"
    DB_PATH: str = "ju_flash.db"

    # JWT
    JWT_SECRET: str = ""
    JWT_ALGORITHM: str = "HS256"
    JWT_EXPIRE_DAYS: int = 7

    # Doubao-Seed-2.0-lite 火山引擎 Ark 配置
    DOUBAO_API_KEY: str = ""
    DOUBAO_EP_ID: str = ""
    DOUBAO_BASE_URL: str = "https://ark.cn-beijing.volces.com/api/v3"

    class Config:
        env_file = ENV_FILE

settings = Settings()

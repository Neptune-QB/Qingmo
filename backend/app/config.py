from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    APP_NAME: str = "青墨 API"
    DB_PATH: str = "ju_flash.db"

    # Doubao-Seed-2.0-lite 火山引擎 Ark 配置
    DOUBAO_API_KEY: str = ""
    DOUBAO_EP_ID: str = "ep-20260514111117-s7m8b"
    DOUBAO_BASE_URL: str = "https://ark.cn-beijing.volces.com/api/v3"

    class Config:
        env_file = ".env"

settings = Settings()

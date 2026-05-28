from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    APP_NAME: str = "青墨 API"
    DATABASE_URL: str = "sqlite+aiosqlite:///./ju_flash.db"

    class Config:
        env_file = ".env"

settings = Settings()

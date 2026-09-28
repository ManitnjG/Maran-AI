import os
from dataclasses import dataclass

@dataclass(frozen=True)
class Settings:
    database_path: str=os.getenv("MARAN_DB_PATH","maran.db")
    model_order: tuple[str,...]=tuple(x.strip() for x in os.getenv("MARAN_MODEL_ORDER","").split(",") if x.strip())
    opencode_enabled: bool=os.getenv("MARAN_OPENCODE_ENABLED","false").lower()=="true"
    max_parallel_workers: int=int(os.getenv("MARAN_MAX_PARALLEL_WORKERS","6"))
    worker_timeout_seconds: int=int(os.getenv("MARAN_WORKER_TIMEOUT_SECONDS","120"))

settings=Settings()

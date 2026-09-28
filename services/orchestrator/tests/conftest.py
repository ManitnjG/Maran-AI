import pytest
from app.web_tools import ToolError

@pytest.fixture(autouse=True)
def isolate_public_web(monkeypatch):
    async def unavailable(*args, **kwargs):
        raise ToolError('Web intentionally disabled in unit tests')
    monkeypatch.setattr('app.executor.research', unavailable)
    monkeypatch.setattr('app.executor.fetch_page', unavailable)

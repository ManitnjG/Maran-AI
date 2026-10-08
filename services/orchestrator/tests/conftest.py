import pytest
from app.web_tools import ToolError

@pytest.fixture(autouse=True)
def isolate_public_web(monkeypatch):
    async def unavailable(*args, **kwargs):
        raise ToolError('Web intentionally disabled in unit tests')
    monkeypatch.setattr('app.executor.research', unavailable)
    monkeypatch.setattr('app.executor.deep_research', unavailable)
    monkeypatch.setattr('app.executor.fetch_page', unavailable)

import json
from app.router import ModelRouter

def brain_response(prompt):
    if prompt.startswith("MARAN_PLAN_JSON"):
        return json.dumps({"steps":json.loads(prompt.split("\nSeed plan: ",1)[1])})
    return "Draft with missing facts clearly identified."

class DraftProvider:
    name = "test"
    def __init__(self): self.calls = 0
    async def complete(self,prompt):
        self.calls += 1
        return brain_response(prompt)

@pytest.fixture(autouse=True)
def isolate_models(monkeypatch):
    monkeypatch.setattr("app.executor.configured_router",lambda:ModelRouter([]))

@pytest.fixture
def provider(monkeypatch):
    p=DraftProvider()
    monkeypatch.setattr("app.executor.configured_router",lambda:ModelRouter([p]))
    return p

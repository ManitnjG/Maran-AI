import json
import httpx
import pytest
from app.models import Mission, PlanStep, MissionStatus
from app.opencode_brain import plan_mission
from app.router import ModelRouter, ProviderError
from app.zen_gateway import OpenCodeZenProvider, free_models
from app.executor import execute_local

class Reply:
    name="opencode-test"
    def __init__(self,value):self.value=value
    async def complete(self,prompt):return self.value

def catalog():
    return {"opencode":{"npm":"@ai-sdk/openai-compatible","models":{
        "free":{"cost":{"input":0,"output":0}},
        "free2":{"cost":{"input":0,"output":0}},
        "paid-free":{"cost":{"input":1,"output":0}},
        "unknown":{"cost":{}},
        "old":{"status":"deprecated","cost":{"input":0,"output":0}},
        "cache":{"cost":{"input":0,"output":0,"cache_read":1}}
    }}}

def test_only_active_confirmed_zero_cost_models():
    assert free_models(catalog())==[("free","chat/completions"),("free2","chat/completions")]

def test_opencode_is_only_registered_provider(monkeypatch):
    from app.providers import configured_router
    monkeypatch.setenv("MARAN_OLLAMA_URL","http://localhost:11434/v1")
    monkeypatch.setenv("MARAN_MODEL_ORDER","other")
    assert [p.provider.name for p in configured_router().providers]==["opencode-zen"]

@pytest.mark.asyncio
@pytest.mark.parametrize("status",[401,403,402,429])
async def test_access_and_quota_do_not_try_other_models(monkeypatch,status):
    real=httpx.AsyncClient
    posts=[]
    def handler(req):
        if req.method=="GET":return httpx.Response(200,json=catalog())
        posts.append(req)
        return httpx.Response(status)
    monkeypatch.setattr("app.zen_gateway.httpx.AsyncClient",lambda **kw:real(transport=httpx.MockTransport(handler),**kw))
    with pytest.raises(ProviderError):await OpenCodeZenProvider().complete("test")
    assert len(posts)==1

@pytest.mark.asyncio
async def test_unavailable_model_falls_back_only_to_free(monkeypatch):
    real=httpx.AsyncClient
    used=[]
    def handler(req):
        if req.method=="GET":return httpx.Response(200,json=catalog())
        used.append(json.loads(req.content)["model"])
        return httpx.Response(503) if len(used)==1 else httpx.Response(200,json={"choices":[{"message":{"content":"draft"}}]})
    monkeypatch.setattr("app.zen_gateway.httpx.AsyncClient",lambda **kw:real(transport=httpx.MockTransport(handler),**kw))
    assert await OpenCodeZenProvider().complete("test")=="draft"
    assert used==["free","free2"]

@pytest.mark.asyncio
async def test_ai_cannot_remove_approval():
    m=Mission(objective="invoice",plan=[PlanStep(id="old",agent="accounting",title="Approved draft",requires_approval=True,approved=True)])
    await plan_mission(m,ModelRouter([Reply('{"steps":[{"agent":"documents","title":"Write a draft"}]}')]))
    accounting=next(s for s in m.plan if s.agent=="accounting")
    assert accounting.requires_approval and accounting.approved
    m=Mission(objective="invoice",plan=[PlanStep(id="old",agent="accounting",title="Approved draft",requires_approval=True,approved=True)])
    await plan_mission(m,ModelRouter([Reply('{"steps":[{"agent":"accounting","title":"Changed task"}]}')]))
    assert m.plan[0].requires_approval and not m.plan[0].approved

@pytest.mark.asyncio
@pytest.mark.parametrize("raw",[
    '{"steps":[{"agent":"shell","title":"run"}]}',
    '{"steps":[{"agent":"documents","title":"run","approved":true}]}',
    '{"steps":[{"agent":"documents","title":"a"},{"agent":"documents","title":"b"}]}',
    'not json'
])
async def test_invalid_model_plan_never_executes(monkeypatch,raw):
    monkeypatch.setattr("app.executor.configured_router",lambda:ModelRouter([Reply(raw)]))
    m=Mission(objective="Draft document",plan=[PlanStep(id="1",agent="documents",title="Draft")])
    done=await execute_local(m)
    assert done.status==MissionStatus.blocked
    assert not any(s.output for s in done.plan)

@pytest.mark.asyncio
async def test_new_ai_sensitive_task_requires_approval(monkeypatch):
    monkeypatch.setattr("app.executor.configured_router",lambda:ModelRouter([Reply('{"steps":[{"agent":"accounting","title":"Draft invoice"}]}')]))
    done=await execute_local(Mission(objective="Prepare figures",plan=[PlanStep(id="1",agent="documents",title="Draft")]))
    assert done.status==MissionStatus.waiting_approval
    assert not any(s.output for s in done.plan)

@pytest.mark.asyncio
async def test_review_failure_blocks_and_retry_preserves_drafts(monkeypatch):
    from conftest import brain_response
    class Reviewer:
        name="opencode-test"
        calls=0
        fail=True
        async def complete(self,prompt):
            self.calls+=1
            if prompt.startswith("MARAN_REVIEW") and self.fail:raise ProviderError("review unavailable")
            return brain_response(prompt)
    p=Reviewer()
    monkeypatch.setattr("app.executor.configured_router",lambda:ModelRouter([p]))
    m=Mission(objective="Write a document",plan=[PlanStep(id="1",agent="documents",title="Draft")])
    result=await execute_local(m)
    assert result.status==MissionStatus.blocked
    assert result.plan[0].status=="completed"
    assert result.plan[-1].status=="blocked"
    saved=result.plan[0].output
    p.fail=False
    before=p.calls
    result=await execute_local(result)
    assert result.status==MissionStatus.completed
    assert result.plan[0].output==saved
    assert p.calls==before+1

import asyncio
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.models import Mission, PlanStep, MissionStatus
from app.executor import execute_local
from app.router import ModelRouter, ProviderError

client = TestClient(app)

from conftest import DraftProvider, brain_response

def test_real_output_persisted_and_run_idempotent(provider):
    m = client.post('/missions', json={'objective': 'Draft a document'}).json()
    done = client.post(f'/missions/{m["id"]}/run').json()
    assert done['status'] == 'completed'
    assert done['verification'] == 'unverified'
    assert done['plan'][0]['output'].startswith('Draft')
    calls = provider.calls
    assert client.post(f'/missions/{m["id"]}/run').json() == done
    assert provider.calls == calls
    assert client.get(f'/missions/{m["id"]}/context').json()['step-1']['provider'] == 'test'

def test_approved_steps_execute_automatically(provider):
    m = client.post('/missions', json={'objective': 'Draft Tally invoice'}).json()
    assert client.post(f'/missions/{m["id"]}/run').json()['status'] == 'waiting_approval'
    assert provider.calls == 0
    done = client.post(f'/missions/{m["id"]}/approval', json={'approved': True}).json()
    assert done['status'] == 'completed'
    assert done['plan'][0]['output']
    assert client.post(f'/missions/{m["id"]}/approval', json={'approved': True}).status_code == 409

def test_cancelled_mission_cannot_run_or_be_approved(provider):
    m = client.post('/missions', json={'objective': 'Draft Tally invoice'}).json()
    client.post(f'/missions/{m["id"]}/approval', json={'approved': False})
    assert client.post(f'/missions/{m["id"]}/run').json()['status'] == 'cancelled'
    assert client.post(f'/missions/{m["id"]}/approval', json={'approved': True}).status_code == 409
    assert provider.calls == 0

def test_missing_provider_blocks_without_fake_completion(monkeypatch):
    monkeypatch.setattr('app.executor.configured_router', lambda: ModelRouter([]))
    m = client.post('/missions', json={'objective': 'Find tour leads'}).json()
    done = client.post(f'/missions/{m["id"]}/run').json()
    assert done['status'] == 'blocked'
    assert done['verification'] == 'unverified'
    assert not done['result']['completed_steps']

def test_auth_required_when_configured(monkeypatch):
    monkeypatch.setenv('MARAN_ACCESS_TOKEN', 'example-test-token')
    assert client.get('/health').status_code == 200
    assert client.get('/missions').status_code == 401
    assert client.get('/missions', headers={'Authorization': 'Bearer example-test-token'}).status_code == 200

@pytest.mark.asyncio
async def test_retry_preserves_successful_steps(provider):
    m = Mission(objective='Draft', status=MissionStatus.blocked, plan=[
        PlanStep(id='1', title='existing', agent='research', status='completed', output='saved'),
        PlanStep(id='2', title='retry', agent='documents', status='blocked')])
    done = await execute_local(m)
    assert done.plan[0].output == 'saved'
    assert provider.calls == 1

@pytest.mark.asyncio
async def test_stop_running_mission_cancels_generation(monkeypatch):
    import httpx
    started = asyncio.Event()
    cancelled = asyncio.Event()
    class Slow:
        name = 'slow'
        async def complete(self, prompt):
            started.set()
            try: await asyncio.Event().wait()
            finally: cancelled.set()
    monkeypatch.setattr('app.executor.configured_router', lambda: ModelRouter([Slow()]))
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='http://test') as c:
        m = (await c.post('/missions', json={'objective': 'Draft document'})).json()
        run = asyncio.create_task(c.post(f'/missions/{m["id"]}/run'))
        await asyncio.wait_for(started.wait(), 2)
        stopped = await c.post(f'/missions/{m["id"]}/stop', json={})
        await run
        assert stopped.json()['status'] == 'cancelled'
        assert cancelled.is_set()
        assert (await c.get(f'/missions/{m["id"]}')).json()['status'] == 'cancelled'

@pytest.mark.asyncio
async def test_empty_model_output_falls_back():
    class Empty:
        name='empty'
        async def complete(self,prompt): return ''
    result, name = await ModelRouter([Empty(),DraftProvider()]).complete('draft')
    assert name == 'test'

@pytest.mark.asyncio
async def test_concurrent_run_requests_share_execution(monkeypatch):
    import httpx
    started, finish = asyncio.Event(), asyncio.Event()
    class Slow:
        name='slow'
        calls=0
        async def complete(self,prompt):
            self.calls += 1
            started.set()
            await finish.wait()
            return brain_response(prompt)
    p=Slow()
    monkeypatch.setattr('app.executor.configured_router',lambda:ModelRouter([p]))
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://test') as c:
        m=(await c.post('/missions',json={'objective':'Draft document'})).json()
        first=asyncio.create_task(c.post(f'/missions/{m["id"]}/run'))
        await asyncio.wait_for(started.wait(),2)
        second=asyncio.create_task(c.post(f'/missions/{m["id"]}/run'))
        await asyncio.sleep(0)
        finish.set()
        results=await asyncio.gather(first,second)
        assert all(r.json()['status']=='completed' for r in results)
        assert p.calls==3

@pytest.mark.asyncio
async def test_keyless_ollama_adapter_omits_authorization(monkeypatch):
    import httpx
    from app.providers import OpenAICompatibleProvider
    real_client=httpx.AsyncClient
    def respond(request):
        assert 'authorization' not in request.headers
        return httpx.Response(200,json={'choices':[{'message':{'content':'Local draft'}}]})
    monkeypatch.delenv('MARAN_OLLAMA_API_KEY',raising=False)
    monkeypatch.setattr('app.providers.httpx.AsyncClient',lambda **kw:real_client(transport=httpx.MockTransport(respond),**kw))
    p=OpenAICompatibleProvider('ollama','http://localhost:11434/v1','MARAN_OLLAMA_API_KEY','local',requires_key=False)
    assert await p.complete('draft')=='Local draft'

@pytest.mark.asyncio
async def test_malformed_provider_response_is_recoverable(monkeypatch):
    import httpx
    from app.providers import OpenAICompatibleProvider
    real_client=httpx.AsyncClient
    monkeypatch.setattr('app.providers.httpx.AsyncClient',lambda **kw:real_client(transport=httpx.MockTransport(lambda request:httpx.Response(200,text='not JSON')),**kw))
    p=OpenAICompatibleProvider('local','http://localhost/v1','UNUSED_TEST_KEY','local',requires_key=False)
    _,name=await ModelRouter([p,DraftProvider()]).complete('draft')
    assert name=='test'

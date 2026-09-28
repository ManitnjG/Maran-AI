import json
from decimal import Decimal
from xml.etree.ElementTree import fromstring
from uuid import uuid4
import pytest
import httpx
from fastapi.testclient import TestClient
from app.main import app
from app.business_tools import SalesVoucher, sales_voucher_xml
from app.web_tools import normalize, public_url, decode_rpc, ToolError, research, fetch_page

client=TestClient(app)

def test_private_urls_rejected():
    for value in ['http://localhost/a','http://127.0.0.1','http://169.254.169.254/latest','file:///tmp/a','https://user:secret@example.com','http://10.0.0.1','http://a.local','http://example.com:8000']:
        with pytest.raises(ToolError): public_url(value)

def test_search_results_have_attributed_contacts():
    text='Title: Company A\nURL: https://example.com/contact\nEmail sales@example.com\n\nTitle: Company A\nURL: https://example.com/contact\n'
    r=normalize({'content':[{'type':'text','text':text}]})
    assert r['sources']==['https://example.com/contact']
    assert r['records'][0]['published_emails']==['sales@example.com']
    assert r['records'][0]['status']=='unqualified_prospect'

def test_missing_sources_are_not_success():
    with pytest.raises(ToolError): normalize({'content':[{'type':'text','text':'No results'}]})

def test_rpc_sse_and_error():
    assert decode_rpc('event: message\ndata: {"jsonrpc":"2.0","id":2,"result":{"ok":true}}\n\n',2)['ok']
    with pytest.raises(ToolError): decode_rpc('{"id":2,"error":{"code":-1}}',2)

@pytest.mark.asyncio
async def test_exa_protocol_and_fetch_schema(monkeypatch):
    real=httpx.AsyncClient
    requests=[]
    def handler(request):
        body=json.loads(request.content); requests.append(body)
        if body['method']=='initialize': result={'protocolVersion':'2024-11-05'}
        elif body['method']=='notifications/initialized': return httpx.Response(202)
        else:
            assert body['params']['arguments']['urls']==['https://example.com']
            result={'content':[{'type':'text','text':'Title: Example\nURL: https://example.com\nPage'}]}
        return httpx.Response(200,json={'jsonrpc':'2.0','id':body['id'],'result':result})
    monkeypatch.setattr('app.web_tools.httpx.AsyncClient',lambda **kw:real(transport=httpx.MockTransport(handler),**kw))
    r=await fetch_page('https://example.com')
    assert len(requests)==3 and r['sources']==['https://example.com']

@pytest.mark.asyncio
async def test_web_rate_limit_becomes_tool_error(monkeypatch):
    real=httpx.AsyncClient
    monkeypatch.setattr('app.web_tools.httpx.AsyncClient',lambda **kw:real(transport=httpx.MockTransport(lambda _:httpx.Response(429)),**kw))
    with pytest.raises(ToolError,match='limit'): await research('test')

def test_research_mission_works_without_model(monkeypatch):
    calls=[]
    async def found(query):
        calls.append(query)
        return normalize({'content':[{'type':'text','text':'Title: Example\nURL: https://example.com\nPublic company listing'}]})
    monkeypatch.setattr('app.executor.research',found)
    m=client.post('/missions',json={'objective':'Find tour leads'}).json()
    done=client.post(f'/missions/{m["id"]}/run').json()
    assert done['status']=='completed'
    assert done['verification']=='partial'
    assert len(calls)==1  # two workers share a single search
    assert done['plan'][0]['evidence']['sources']==['https://example.com']
    export=client.get(f'/missions/{m["id"]}/export').json()
    assert 'https://example.com' in export['content']

def voucher():
    return dict(company='A & B',customer_ledger='Customer <X>',sales_ledger='Sales',voucher_number='V1',voucher_date='2026-09-28',amount='1500.25')

def test_tally_export_balances_and_escapes():
    req=SalesVoucher(**voucher())
    root=fromstring(sales_voucher_xml(req))
    assert root.find('.//SVCURRENTCOMPANY').text=='A & B'
    assert root.find('.//PARTYLEDGERNAME').text=='Customer <X>'
    assert sum(Decimal(x.text) for x in root.findall('.//AMOUNT'))==0
    assert root.find('.//ISINVOICE').text=='No'
    assert client.post('/tools/tally-voucher',json=voucher()).json()['content']

@pytest.mark.parametrize('amount',['-1','0','NaN','1.123'])
def test_invalid_voucher_amount_rejected(amount):
    assert client.post('/tools/tally-voucher',json={**voucher(),'amount':amount}).status_code==422

def test_backup_restore_does_not_run_or_overwrite():
    mid=str(uuid4())
    payload={'schema_version':1,'missions':[{'id':mid,'objective':'Do work','status':'running','plan':[]}]}
    assert client.post('/backup/restore',json=payload).json()['restored']==1
    assert client.get(f'/missions/{mid}').json()['status']=='blocked'
    assert client.post('/backup/restore',json=payload).status_code==409
    assert client.post('/backup/restore',json={'schema_version':2,'missions':[]}).status_code==422
    backup=client.get('/backup').json()
    assert any(m['id']==mid for m in backup['missions'])

def test_production_requires_strong_token(monkeypatch):
    monkeypatch.setenv('MARAN_ENV','production')
    monkeypatch.setenv('MARAN_ACCESS_TOKEN','short')
    with pytest.raises(RuntimeError,match='Production requires'):
        with TestClient(app): pass

def test_restart_recovers_interrupted_mission():
    from app.models import Mission,MissionStatus,PlanStep
    from app.store import store
    m=Mission(objective='Interrupted',status=MissionStatus.running,plan=[PlanStep(id='1',title='Draft',agent='documents',status='running')])
    store.put(m)
    with TestClient(app) as c:
        recovered=c.get(f'/missions/{m.id}').json()
        assert recovered['status']=='blocked'
        assert recovered['plan'][0]['status']=='pending'

def test_tamil_worker_and_wake_prefix():
    result=client.post('/voice/interpret',json={'text':'மாறன் பணியாளர் உருவாக்கு சுற்றுலா உதவியாளர்'}).json()
    assert result['action']=='create_worker'
    assert result['worker']['name']=='சுற்றுலா உதவியாளர்'
    assert client.post('/voice/interpret',json={'text':'மாறன் அனைத்தையும் நிறுத்து'}).json()['action']=='confirm'

@pytest.mark.asyncio
async def test_opencode_denies_tools_and_isolates_process(monkeypatch):
    from app.opencode_provider import OpenCodeCLIProvider
    seen={}
    class Process:
        returncode=0
        async def communicate(self):
            return b'{"type":"text","part":{"text":"Draft"}}\n',b''
    async def spawn(*args,**kwargs):
        seen.update(kwargs)
        assert '--model' in args and args[-1]=='test prompt'
        return Process()
    monkeypatch.setattr('app.opencode_provider.shutil.which',lambda _: '/test/opencode')
    monkeypatch.setattr('app.opencode_provider.asyncio.create_subprocess_exec',spawn)
    monkeypatch.setenv('PRIVATE_HOST_SECRET','do-not-pass')
    assert await OpenCodeCLIProvider('opencode/big-pickle').complete('test prompt')=='Draft'
    assert 'PRIVATE_HOST_SECRET' not in seen['env']
    assert json.loads(seen['env']['OPENCODE_PERMISSION'])=={'*':'deny'}

@pytest.mark.asyncio
async def test_opencode_provider_errors_are_not_success(monkeypatch):
    from app.opencode_provider import OpenCodeCLIProvider
    from app.router import ProviderError
    class Process:
        returncode=0
        async def communicate(self):return b'{"type":"error","error":{"message":"denied"}}',b''
    async def spawn(*args,**kwargs):return Process()
    monkeypatch.setattr('app.opencode_provider.shutil.which',lambda _: '/test/opencode')
    monkeypatch.setattr('app.opencode_provider.asyncio.create_subprocess_exec',spawn)
    with pytest.raises(ProviderError):await OpenCodeCLIProvider('opencode/big-pickle').complete('test')

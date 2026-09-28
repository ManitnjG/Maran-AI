from fastapi.testclient import TestClient
from app.main import app

client=TestClient(app)

def test_health():
    assert client.get("/health").json()["ok"] is True

def test_create_mission():
    r=client.post("/missions",json={"objective":"Find and verify corporate tour leads"})
    assert r.status_code==200
    body=r.json()
    assert "tour_leads" in body["assigned_agents"]
    assert "verifier" in body["assigned_agents"]

def test_accounting_waits_for_approval():
    body=client.post("/missions",json={"objective":"Create Tally invoice"}).json()
    assert body["status"]=="waiting_approval"
    updated=client.post(f'/missions/{body["id"]}/approval',json={"approved":True}).json()
    assert updated["status"]=="running"


def test_manager_controls_assignment_and_stop():
    body=client.post("/missions",json={"objective":"SEO research for travel website"}).json()
    assert any(e["type"]=="manager_assigned" for e in body["events"])
    stopped=client.post(f'/missions/{body["id"]}/stop',json={"agent_id":"seo","reason":"No longer needed"}).json()
    assert any(e["type"]=="agent_stopped" and e["agent"]=="seo" for e in stopped["events"])

def test_manager_can_stop_entire_workforce():
    body=client.post("/missions",json={"objective":"Research Chennai tourism"}).json()
    stopped=client.post(f'/missions/{body["id"]}/stop',json={"reason":"User cancelled"}).json()
    assert stopped["status"]=="cancelled"


def test_low_confidence_voice_requires_confirmation():
    r=client.post("/voice/interpret",json={"text":"create a quotation worker","confidence":0.55})
    assert r.json()["action"]=="confirm"

def test_destructive_voice_requires_confirmation():
    r=client.post("/voice/interpret",json={"text":"stop all workers","confidence":0.99})
    assert r.json()["action"]=="confirm"

def test_normal_voice_becomes_mission():
    r=client.post("/voice/interpret",json={"text":"find corporate tour leads","confidence":0.95})
    assert r.json()["action"]=="mission"


def test_dynamic_worker_create_reuse_and_cleanup():
    one=client.post("/workers",json={"name":"Hotel Quotation Worker","skills":["hotel","quotation"],"temporary":True}).json()
    two=client.post("/workers",json={"name":"Another Quote Worker","skills":["hotel","quotation"],"temporary":True}).json()
    assert one["id"]==two["id"]
    listed=client.get("/workers").json()
    assert any(w["id"]==one["id"] for w in listed)
    cleaned=client.post("/workers/cleanup").json()
    assert one["id"] in cleaned["stopped"]


def test_voice_can_create_dynamic_worker():
    r=client.post("/voice/interpret",json={"text":"create worker Hotel Quotation Specialist","confidence":0.98})
    body=r.json()
    assert body["action"]=="create_worker"
    assert body["worker"]["name"]=="Hotel Quotation Specialist"
    assert "hotel" in body["worker"]["skills"]


def test_context_vault_endpoint_exists_for_mission():
    m=client.post("/missions",json={"objective":"research tour market"}).json()
    r=client.get(f'/missions/{m["id"]}/context')
    assert r.status_code==200
    assert isinstance(r.json(),dict)


def test_stopped_dynamic_worker_disappears():
    w=client.post("/workers",json={"name":"Temporary Unique Pricing Worker","skills":["unique_pricing_skill"],"temporary":True}).json()
    assert client.delete(f'/workers/{w["id"]}').status_code==200
    assert not any(x["id"]==w["id"] for x in client.get("/workers").json())


def test_dynamic_worker_is_assigned_then_released():
    w=client.post("/workers",json={"name":"Cruise Pricing Specialist","skills":["cruise","pricing"],"temporary":True}).json()
    m=client.post("/missions",json={"objective":"research cruise pricing options"}).json()
    assert w["id"] in m["assigned_agents"]
    done=client.post(f'/missions/{m["id"]}/run').json()
    assert any(e.get("type")=="temporary_workers_released" and w["id"] in e.get("agents",[]) for e in done["events"])
    assert not any(x["id"]==w["id"] for x in client.get("/workers").json())

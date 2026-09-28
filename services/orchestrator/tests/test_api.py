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

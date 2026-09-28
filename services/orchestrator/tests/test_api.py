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

from fastapi.testclient import TestClient
from app.main import app
from app.safety import assess, AUTO, CONFIRM, BLOCKED

client = TestClient(app)

def test_safety_policy_keeps_research_automatic_but_blocks_authentication():
    assert assess("research", "Research hotel options", "Book a hotel later").level == AUTO
    assert assess("accounting", "Prepare an invoice draft", "Invoice customer").level == CONFIRM
    assert assess("research", "Read OTP and continue", "Login").level == BLOCKED

def test_autonomous_mission_learns_successful_workflow(provider):
    created = client.post("/missions", json={
        "objective": "Prepare a document draft for autonomy regression test",
        "autonomous": True,
        "max_cycles": 3,
    }).json()
    done = client.post(f"/missions/{created['id']}/autonomous-run").json()
    assert done["status"] == "completed"
    assert done["cycle"] >= 1
    assert done["learned_skill_id"]
    types = [e["type"] for e in done["events"]]
    assert "autonomy_cycle_started" in types
    assert "skill_learned" in types
    assert "autonomy_finished" in types

def test_learned_skills_are_queryable(provider):
    created = client.post("/missions", json={
        "objective": "Prepare a PDF document workflow for learned skill query",
        "autonomous": True,
    }).json()
    client.post(f"/missions/{created['id']}/autonomous-run")
    skills = client.get("/skills").json()
    assert any(s["success_count"] >= 1 for s in skills)

def test_workspace_memory_rejects_secret_like_values():
    ok = client.post("/memory", json={
        "key": "tour_style",
        "value": "Prefer concise itineraries",
    })
    assert ok.status_code == 200
    bad = client.post("/memory", json={
        "key": "api_key",
        "value": "secret-value",
    })
    assert bad.status_code == 422

def test_protected_goal_cannot_be_approved_into_execution(provider):
    created = client.post("/missions", json={
        "objective": "Use OTP and password to login and continue",
        "autonomous": True,
        "max_cycles": 2,
    }).json()
    done = client.post(f"/missions/{created['id']}/autonomous-run").json()
    assert done["status"] == "blocked"
    assert any(s["risk_level"] == "blocked" for s in done["plan"])
    assert any(e["type"] == "autonomy_exhausted" for e in done["events"])

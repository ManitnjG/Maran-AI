from app.planner import local_plan

def test_tour_lead_plan():
    agents,steps=local_plan("Find and verify corporate tour leads")
    assert "tour_leads" in agents
    assert "verifier" in agents
    assert steps[-1].agent=="verifier"

def test_accounting_requires_approval():
    _,steps=local_plan("Create Tally invoice")
    assert any(s.requires_approval for s in steps)

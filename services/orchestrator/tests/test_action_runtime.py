import pytest
from app.action_runtime import propose_actions, execute_action, candidate_tools
from app.models import Mission, PlanStep

class ActionRouter:
    def __init__(self, payload):
        self.payload=payload
    async def complete(self, prompt):
        return self.payload, "test"

@pytest.mark.asyncio
async def test_action_proposal_requires_grounded_recipient(monkeypatch):
    monkeypatch.setattr("app.action_runtime.integration_status",lambda:{"gmail_send":{"status":"ready"}})
    mission=Mission(
        objective="Send email to buyer@example.com with the prepared introduction",
        plan=[PlanStep(id="1",title="Draft",agent="email",status="completed",output="To buyer@example.com. Subject: Hello. Body: Welcome.")]
    )
    router=ActionRouter('{"actions":[{"tool_id":"gmail_send","title":"Send introduction","args":{"to":"buyer@example.com","subject":"Hello","body":"Welcome"}}]}')
    actions=await propose_actions(mission,router)
    assert len(actions)==1
    assert actions[0].tool_id=="gmail_send"
    assert not actions[0].approved
    assert actions[0].status=="waiting_approval"

@pytest.mark.asyncio
async def test_action_proposal_rejects_invented_recipient(monkeypatch):
    monkeypatch.setattr("app.action_runtime.integration_status",lambda:{"gmail_send":{"status":"ready"}})
    mission=Mission(
        objective="Send email after drafting it",
        plan=[PlanStep(id="1",title="Draft",agent="email",status="completed",output="Generic email draft without a recipient.")]
    )
    router=ActionRouter('{"actions":[{"tool_id":"gmail_send","title":"Send","args":{"to":"invented@example.com","subject":"Hi","body":"Hello"}}]}')
    assert await propose_actions(mission,router)==[]

@pytest.mark.asyncio
async def test_approved_action_executes_and_records_provider_ack(monkeypatch):
    async def fake_send(to,subject,body):
        return {"id":"msg-123","to":to}
    monkeypatch.setattr("app.action_runtime.integration_status",lambda:{"gmail_send":{"status":"ready"}})
    monkeypatch.setattr("app.action_runtime.gmail_send",fake_send)
    mission=Mission(
        objective="Send email to buyer@example.com",
        plan=[PlanStep(id="1",title="Draft",agent="email",status="completed",output="buyer@example.com")]
    )
    router=ActionRouter('{"actions":[{"tool_id":"gmail_send","title":"Send","args":{"to":"buyer@example.com","subject":"Hi","body":"Hello"}}]}')
    action=(await propose_actions(mission,router))[0]
    action.approved=True
    done=await execute_action(action)
    assert done.status=="completed"
    assert done.verification=="provider_acknowledged"
    assert done.result["id"]=="msg-123"


def test_natural_action_phrasing_maps_to_connector_tools():
    assert "whatsapp_send" in candidate_tools("Send message in WhatsApp to +919876543210")
    assert "gmail_send" in candidate_tools("Use Gmail to send a message")
    assert "google_calendar" in candidate_tools("Create a meeting in my calendar")
    assert "google_drive" in candidate_tools("Drive upload for this document")
    assert "github_actions" in candidate_tools("Release APK with GitHub workflow")
    assert "instagram_post" in candidate_tools("Publish on Instagram")
    assert "tally_post" in candidate_tools("Create a Tally entry")

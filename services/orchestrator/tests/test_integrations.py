from fastapi.testclient import TestClient
from app.main import app
from app.integrations import integration_status

client = TestClient(app)

def test_integration_matrix_has_requested_capabilities():
    s = integration_status()
    for key in ["gmail_send","gmail_read","gmail_reply","google_calendar","google_drive",
                "whatsapp_send","instagram_post","facebook_post","linkedin_post",
                "tally_post","customer_calling","sms_send","crm","github_actions",
                "apk_from_phone","captcha_bypass","otp_bypass"]:
        assert key in s

def test_external_action_requires_confirmation():
    r = client.post('/actions/gmail/send', json={"to":"a@example.com","subject":"Hi","body":"Test","confirmed":False})
    assert r.status_code == 409

def test_crm_roundtrip():
    r = client.post('/crm/leads', json={"name":"Test Lead","company":"Example"})
    assert r.status_code == 200
    lead = r.json()
    r2 = client.post(f"/crm/leads/${lead['id']}/status", json={"status":"contacted"})
    assert r2.status_code == 200
    assert r2.json()["status"] == "contacted"

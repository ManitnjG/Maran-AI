from fastapi.testclient import TestClient
from app.main import app

client=TestClient(app)

def test_knowledge_upload_search_and_delete():
    ws="knowledge-test-workspace"
    uploaded=client.post(
        "/knowledge/upload",
        data={"workspace_id":ws,"allow_ai":"true"},
        files={"file":("tour-notes.txt",b"Madurai heritage itinerary includes Meenakshi Amman Temple and Thirumalai Nayakkar Palace.","text/plain")},
    )
    assert uploaded.status_code==200
    doc=uploaded.json()
    assert doc["allow_ai"] is True
    found=client.get("/knowledge/search",params={"workspace_id":ws,"q":"Madurai Meenakshi"}).json()
    assert found and found[0]["document_id"]==doc["id"]
    assert client.delete(f"/knowledge/{doc['id']}",params={"workspace_id":ws}).status_code==200

def test_workspace_delete_requires_confirmation():
    r=client.post("/workspace/delete",json={"confirmed":False,"workspace_id":"delete-test"})
    assert r.status_code==409

def test_workspace_delete_clears_scoped_data():
    ws="delete-test-workspace"
    client.post("/memory",json={"workspace_id":ws,"key":"style","value":"concise"})
    client.post(
        "/knowledge/upload",
        data={"workspace_id":ws,"allow_ai":"false"},
        files={"file":("notes.txt",b"private searchable reference text","text/plain")},
    )
    mission=client.post("/missions",json={"objective":"Prepare a document","workspace_id":ws,"autonomous":False}).json()
    result=client.post("/workspace/delete",json={"confirmed":True,"workspace_id":ws})
    assert result.status_code==200
    body=result.json()
    assert body["missions_deleted"]>=1
    assert body["memory_deleted"]>=1
    assert body["knowledge_deleted"]>=1
    assert not any(m["id"]==mission["id"] for m in client.get("/missions").json())

from __future__ import annotations

import io
import re
import sqlite3
from datetime import datetime, timezone
from threading import RLock
from uuid import uuid4

from .config import settings

MAX_FILE_BYTES = 5_000_000
MAX_TEXT_CHARS = 250_000

class KnowledgeError(ValueError):
    pass

def _extract_text(name: str, mime_type: str, data: bytes) -> str:
    if len(data) > MAX_FILE_BYTES:
        raise KnowledgeError("File is larger than 5 MB")
    lower = name.lower()
    if lower.endswith(".pdf") or mime_type == "application/pdf":
        from pypdf import PdfReader
        reader = PdfReader(io.BytesIO(data))
        text = "\n".join((page.extract_text() or "") for page in reader.pages)
    elif lower.endswith(".docx") or mime_type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
        from docx import Document
        doc = Document(io.BytesIO(data))
        text = "\n".join(p.text for p in doc.paragraphs)
    elif lower.endswith((".txt",".md",".csv",".json",".log")) or mime_type.startswith("text/") or mime_type == "application/json":
        text = data.decode("utf-8", errors="replace")
    else:
        raise KnowledgeError("Supported knowledge files: PDF, DOCX, TXT, MD, CSV and JSON")
    text = re.sub(r"\x00", "", text).strip()
    if not text:
        raise KnowledgeError("No readable text was found in this file")
    return text[:MAX_TEXT_CHARS]

def _chunks(text: str, size: int = 1400, overlap: int = 180) -> list[str]:
    out=[]
    pos=0
    while pos < len(text):
        end=min(len(text), pos+size)
        chunk=text[pos:end].strip()
        if chunk: out.append(chunk)
        if end >= len(text): break
        pos=max(pos+1, end-overlap)
    return out[:220]

class KnowledgeStore:
    def __init__(self):
        self.lock=RLock()
        self.db=sqlite3.connect(settings.database_path,check_same_thread=False)
        self.db.execute("""CREATE TABLE IF NOT EXISTS knowledge_docs(
            id TEXT PRIMARY KEY,
            workspace_id TEXT NOT NULL,
            name TEXT NOT NULL,
            mime_type TEXT NOT NULL,
            allow_ai INTEGER NOT NULL,
            text TEXT NOT NULL,
            created_at TEXT NOT NULL
        )""")
        self.db.commit()

    def add(self, workspace_id: str, name: str, mime_type: str, data: bytes, allow_ai: bool):
        text=_extract_text(name,mime_type or "application/octet-stream",data)
        item={
            "id":"doc-"+str(uuid4()),
            "workspace_id":workspace_id,
            "name":name[:240],
            "mime_type":(mime_type or "application/octet-stream")[:120],
            "allow_ai":bool(allow_ai),
            "created_at":datetime.now(timezone.utc).isoformat(),
        }
        with self.lock,self.db:
            self.db.execute(
                "INSERT INTO knowledge_docs VALUES(?,?,?,?,?,?,?)",
                (item["id"],workspace_id,item["name"],item["mime_type"],int(item["allow_ai"]),text,item["created_at"])
            )
        item["characters"]=len(text)
        return item

    def list(self, workspace_id: str="default"):
        with self.lock:
            rows=self.db.execute(
                "SELECT id,name,mime_type,allow_ai,length(text),created_at FROM knowledge_docs WHERE workspace_id=? ORDER BY created_at DESC",
                (workspace_id,)
            ).fetchall()
        return [{"id":r[0],"name":r[1],"mime_type":r[2],"allow_ai":bool(r[3]),"characters":r[4],"created_at":r[5]} for r in rows]

    def search(self, query: str, workspace_id: str="default", ai_only: bool=False, limit: int=8):
        terms=[t.lower() for t in re.findall(r"[\w-]{3,}",query,flags=re.UNICODE)][:20]
        if not terms: return []
        sql="SELECT id,name,allow_ai,text FROM knowledge_docs WHERE workspace_id=?"
        args=[workspace_id]
        if ai_only:
            sql+=" AND allow_ai=1"
        with self.lock:
            rows=self.db.execute(sql,args).fetchall()
        scored=[]
        for doc_id,name,allow_ai,text in rows:
            for idx,chunk in enumerate(_chunks(text)):
                low=chunk.lower()
                score=sum(low.count(term) for term in terms)
                if score:
                    scored.append((score,doc_id,name,bool(allow_ai),idx,chunk))
        scored.sort(key=lambda x:x[0],reverse=True)
        return [{
            "document_id":x[1],"name":x[2],"allow_ai":x[3],"chunk":x[4],
            "snippet":x[5][:1800],"score":x[0],
        } for x in scored[:max(1,min(limit,20))]]

    def delete(self, doc_id: str, workspace_id: str="default") -> bool:
        with self.lock,self.db:
            cur=self.db.execute("DELETE FROM knowledge_docs WHERE id=? AND workspace_id=?",(doc_id,workspace_id))
        return cur.rowcount>0

    def clear(self, workspace_id: str="default") -> int:
        with self.lock,self.db:
            cur=self.db.execute("DELETE FROM knowledge_docs WHERE workspace_id=?",(workspace_id,))
        return cur.rowcount

knowledge_store=KnowledgeStore()

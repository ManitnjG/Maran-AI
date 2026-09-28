import re
from .models import VoiceCommand

DESTRUCTIVE=("stop all","delete","remove worker","cancel mission")
CREATE_MARKERS=("create worker","add worker","create agent","add agent","worker create","agent create")

def _worker_request(text:str):
    low=text.lower()
    marker=next((m for m in CREATE_MARKERS if m in low),None)
    if not marker:return None
    tail=text[low.index(marker)+len(marker):].strip(" :-,")
    name=(tail or "Specialist Worker").strip()
    # Human speech can name the specialty naturally; Manager derives reusable skill tokens.
    skills=[w.lower() for w in re.findall(r"[A-Za-z][A-Za-z0-9_-]{2,}",name) if w.lower() not in {"worker","agent","specialist","create","add"}]
    return {"action":"create_worker","name":name[:80],"skills":skills[:12] or ["general"],"temporary":True}

def interpret(command:VoiceCommand)->dict:
    text=command.text.strip()
    low=text.lower()
    sensitive=any(x in low for x in DESTRUCTIVE)
    if command.confidence < 0.78 or sensitive:
        return {"action":"confirm","heard":text,"reason":"low_confidence" if command.confidence<0.78 else "consequential_command"}
    worker=_worker_request(text)
    if worker:return worker
    return {"action":"mission","objective":text}

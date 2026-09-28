import re
from .models import VoiceCommand

DESTRUCTIVE=("நிறுத்து","நீக்கு","ரத்து","stop all","delete","remove worker","cancel mission")
CREATE_MARKERS=("பணியாளரை உருவாக்கு","பணியாளர் உருவாக்கு","ஏஜென்ட் உருவாக்கு","create worker","add worker","create agent","add agent","worker create","agent create")

def _worker_request(text:str):
    low=text.lower()
    marker=next((m for m in CREATE_MARKERS if m in low),None)
    if not marker:return None
    tail=text[low.index(marker)+len(marker):].strip(" :-,")
    name=(tail or "Specialist Worker").strip()
    # Human speech can name the specialty naturally; Manager derives reusable skill tokens.
    skills=[w.lower() for w in re.findall(r"[^\W\d][\w-]{2,}",name,flags=re.UNICODE) if w.lower() not in {"worker","agent","specialist","create","add"}]
    return {"action":"create_worker","name":name[:80],"skills":skills[:12] or ["general"],"temporary":True}

def interpret(command:VoiceCommand)->dict:
    text=re.sub(r"^(?:hey\s+)?(?:maran|மாறன்|மாரன்)[ ,:]*", "", command.text.strip(), flags=re.I).strip()
    if not text:return {"action":"confirm","heard":command.text,"reason":"missing_command"}
    low=text.lower()
    sensitive=any(x in low for x in DESTRUCTIVE)
    if command.confidence < 0.78 or sensitive:
        return {"action":"confirm","heard":text,"reason":"low_confidence" if command.confidence<0.78 else "consequential_command"}
    worker=_worker_request(text)
    if worker:return worker
    return {"action":"mission","objective":text}

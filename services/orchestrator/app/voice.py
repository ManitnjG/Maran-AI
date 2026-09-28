from .models import VoiceCommand

DESTRUCTIVE=("stop all","delete","remove worker","cancel mission")
def interpret(command:VoiceCommand)->dict:
    text=command.text.strip()
    low=text.lower()
    sensitive=any(x in low for x in DESTRUCTIVE)
    # Low-confidence or destructive speech is never silently executed.
    if command.confidence < 0.78 or sensitive:
        return {"action":"confirm","heard":text,"reason":"low_confidence" if command.confidence<0.78 else "consequential_command"}
    return {"action":"mission","objective":text}

from dataclasses import dataclass

@dataclass(frozen=True)
class Agent:
    id: str
    name: str
    skills: tuple[str, ...]
    permissions: tuple[str, ...]

AGENTS = {
 "research": Agent("research","Research Agent",("research","verify","web"),("read_public_web",)),
 "tour_leads": Agent("tour_leads","Tour Lead Scout",("tourism","leads","enrichment"),("read_public_web",)),
 "seo": Agent("seo","SEO Agent",("seo","keywords","audit"),("read_public_web",)),
 "social": Agent("social","Social Marketing Agent",("social","content","analytics"),("read_public_web",)),
 "documents": Agent("documents","Document Agent",("documents","ocr","pdf"),("read_files","create_files")),
 "accounting": Agent("accounting","Accounting Agent",("invoice","gst","tally"),("read_accounting","draft_accounting")),
 "coding": Agent("coding","OpenCode Worker",("coding","github","testing"),("read_code","draft_code")),
 "verifier": Agent("verifier","Verifier",("qa","evidence","validation"),("read_outputs",)),
}

def select_agents(objective: str) -> list[str]:
    q=objective.lower()
    selected=[]
    rules={
      "tour_leads":("tour lead","travel lead","tour enquiry","tour inquiry"),
      "seo":("seo","keyword","search ranking"),
      "social":("instagram","social media","youtube","facebook","reel"),
      "documents":("pdf","document","ocr","word","excel"),
      "accounting":("gst","tally","invoice","accounts"),
      "coding":("code","android","github","apk","software","app"),
      "research":("research","find","verify","compare","search"),
    }
    for agent,words in rules.items():
        if any(w in q for w in words): selected.append(agent)
    if not selected: selected=["research"]
    if "verifier" not in selected: selected.append("verifier")
    return selected

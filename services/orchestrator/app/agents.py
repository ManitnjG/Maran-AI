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
 "itinerary": Agent("itinerary","Tour Itinerary & Quotation Agent",("itinerary","tour package","quotation","pricing"),("read_public_web","read_workspace_memory")),
 "seo": Agent("seo","SEO Agent",("seo","keywords","audit"),("read_public_web",)),
 "marketing": Agent("marketing","Marketing Agent",("campaign","content","positioning","outreach"),("read_public_web","draft_content")),
 "social": Agent("social","Social Marketing Agent",("social","content","analytics"),("read_public_web","draft_content")),
 "email": Agent("email","Email Agent",("email","outreach","reply","follow-up"),("draft_communications",)),
 "calendar": Agent("calendar","Calendar Agent",("calendar","schedule","meeting"),("draft_calendar",)),
 "business_admin": Agent("business_admin","Business Admin Agent",("crm","quotation","follow-up","operations"),("read_workspace_memory","draft_business_actions")),
 "documents": Agent("documents","Document & PDF Agent",("documents","ocr","pdf","word","excel"),("read_files","create_files")),
 "knowledge": Agent("knowledge","Knowledge Agent",("knowledge","files","reference","search documents"),("read_user_knowledge",)),
 "accounting": Agent("accounting","Accounting Agent",("invoice","gst","tally"),("read_accounting","draft_accounting")),
 "coding": Agent("coding","OpenCode Worker",("coding","github","testing"),("read_code","draft_code")),
 "verifier": Agent("verifier","Verifier",("qa","evidence","validation"),("read_outputs",)),
}

def select_agents(objective: str) -> list[str]:
    q=objective.lower()
    selected=[]
    rules={
      "tour_leads":("tour lead","travel lead","tour enquiry","tour inquiry","corporate tour","சுற்றுலா","வாடிக்கையாளர்"),
      "itinerary":("itinerary","tour package","quotation","travel plan","tour plan","package rate"),
      "seo":("seo","keyword","search ranking","தேடுபொறி"),
      "marketing":("marketing","campaign","promotion","advertise","brand"),
      "social":("instagram","social media","youtube","facebook","reel","linkedin","சமூக ஊடகம்"),
      "email":("email","mail","outreach","follow-up email","reply email"),
      "calendar":("calendar","schedule meeting","appointment","create event"),
      "business_admin":("crm","quotation","proposal","follow up","business admin","operations"),
      "documents":("pdf","document","ocr","word","excel","ஆவணம்"),
      "knowledge":("my file","my document","knowledge","uploaded file","reference file"),
      "accounting":("gst","tally","invoice","accounts","கணக்கு","விலைப்பட்டியல்"),
      "coding":("code","android","github","apk","software","app"),
      "research":("research","find","verify","compare","search","தேடு","ஆராய்ச்சி"),
    }
    for agent,words in rules.items():
        if any(w in q for w in words): selected.append(agent)
    if not selected: selected=["research"]
    if "verifier" not in selected: selected.append("verifier")
    return selected

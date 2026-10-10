package ai.maran.app.ui

internal enum class ActionKind(val connectorId:String?) {
    WHATSAPP("whatsapp_send"),
    EMAIL("gmail_send"),
    SMS("sms_send"),
    CALENDAR("google_calendar"),
    DRIVE("google_drive"),
    GITHUB("github_actions"),
    INSTAGRAM("instagram_post"),
    FACEBOOK("facebook_post"),
    LINKEDIN("linkedin_post"),
    TALLY("tally_post"),
    TOUR_MISSION(null),
    RESEARCH_MISSION(null),
    DOCUMENT_MISSION(null),
    MARKETING_MISSION(null)
}

internal data class ActionRoute(
    val kind:ActionKind,
    val normalized:String,
    val localFallback:Boolean=false
)

private fun hasActionVerb(text:String,vararg verbs:String):Boolean =
    verbs.any { Regex("""\b\${Regex.escape(it)}\b""",RegexOption.IGNORE_CASE).containsMatchIn(text) }

internal fun actionCommandRoute(raw:String):ActionRoute? {
    val text=raw.trim()
        .replace(Regex("""^(?i:(?:hey\s+)?maran)\s*[,.:!]*\s*"""),"")
        .replace(Regex("""^(?i:(?:please\s+)?(?:can|could|would)\s+you\s+|please\s+)"""),"")
        .trim()
    if(text.isBlank()) return null
    val q=text.lowercase()

    if("whatsapp" in q && hasActionVerb(q,"send","message","text","reply","share","write"))
        return ActionRoute(ActionKind.WHATSAPP,text,localFallback=true)

    if((Regex("""\b(?:email|e-mail|gmail|mail)\b""",RegexOption.IGNORE_CASE).containsMatchIn(q)) &&
        hasActionVerb(q,"send","reply","compose","draft","write","mail"))
        return ActionRoute(ActionKind.EMAIL,text,localFallback=true)

    if((Regex("""\b(?:sms|text message|message)\b""",RegexOption.IGNORE_CASE).containsMatchIn(q)) &&
        hasActionVerb(q,"send","text","message","reply"))
        return ActionRoute(ActionKind.SMS,text,localFallback=true)

    if((Regex("""\b(?:calendar|meeting|appointment|event)\b""",RegexOption.IGNORE_CASE).containsMatchIn(q)) &&
        hasActionVerb(q,"schedule","create","add","book","set"))
        return ActionRoute(ActionKind.CALENDAR,text,localFallback=true)

    if((q.contains("google drive") || Regex("""\bdrive\b""").containsMatchIn(q)) &&
        hasActionVerb(q,"upload","save","create","store"))
        return ActionRoute(ActionKind.DRIVE,text)

    if((q.contains("github") || q.contains("apk") || q.contains("workflow")) &&
        hasActionVerb(q,"push","update","commit","run","trigger","build","release","create"))
        return ActionRoute(ActionKind.GITHUB,text)

    if("instagram" in q && hasActionVerb(q,"post","publish","share","upload"))
        return ActionRoute(ActionKind.INSTAGRAM,text,localFallback=true)
    if("facebook" in q && hasActionVerb(q,"post","publish","share","upload"))
        return ActionRoute(ActionKind.FACEBOOK,text,localFallback=true)
    if("linkedin" in q && hasActionVerb(q,"post","publish","share","upload"))
        return ActionRoute(ActionKind.LINKEDIN,text,localFallback=true)

    if("tally" in q && hasActionVerb(q,"post","create","make","prepare","enter","record","invoice","voucher"))
        return ActionRoute(ActionKind.TALLY,text)

    if(
        q.contains("tour lead") || q.contains("travel lead") ||
        (q.contains("tour") && (q.contains("quotation") || q.contains("itinerary") || q.contains("package")))
    ) return ActionRoute(ActionKind.TOUR_MISSION,text)

    if(
        Regex("""\b(?:research|compare|verify|find leads?|market research|competitor research)\b""",RegexOption.IGNORE_CASE)
            .containsMatchIn(q)
    ) return ActionRoute(ActionKind.RESEARCH_MISSION,text)

    if(
        Regex("""\b(?:pdf|document|docx|quotation|proposal|report)\b""",RegexOption.IGNORE_CASE).containsMatchIn(q) &&
        hasActionVerb(q,"create","prepare","make","generate","draft","write")
    ) return ActionRoute(ActionKind.DOCUMENT_MISSION,text)

    if(
        Regex("""\b(?:seo|marketing|campaign|promotion|social media)\b""",RegexOption.IGNORE_CASE).containsMatchIn(q) &&
        hasActionVerb(q,"create","prepare","run","plan","make","research","improve")
    ) return ActionRoute(ActionKind.MARKETING_MISSION,text)

    return null
}

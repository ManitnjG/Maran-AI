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

private fun containsAny(text:String,vararg values:String):Boolean =
    values.any { text.contains(it,ignoreCase=true) }

private fun hasActionVerb(text:String,vararg verbs:String):Boolean =
    verbs.any { verb ->
        if(verb.any { it.code>127 }) text.contains(verb,ignoreCase=true)
        else Regex("""\b""" + Regex.escape(verb) + """\b""",RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

internal fun actionCommandRoute(raw:String):ActionRoute? {
    val text=raw.trim()
        .replace(Regex("""^(?i:(?:hey\s+)?(?:maran|மாறன்|மாரன்))\s*[,.:!]*\s*"""),"")
        .replace(Regex("""^(?i:(?:please\s+)?(?:can|could|would)\s+you\s+|please\s+)"""),"")
        .trim()
    if(text.isBlank()) return null
    val q=text.lowercase()

    if(containsAny(q,"whatsapp","வாட்ஸ்அப்","வாட்ஸ்அப்ப") &&
        hasActionVerb(q,"send","message","text","reply","share","write","அனுப்பு","செய்தி","பதில்","பகிர்"))
        return ActionRoute(ActionKind.WHATSAPP,text,localFallback=true)

    if(containsAny(q,"email","e-mail","gmail","mail","மின்னஞ்சல்") &&
        hasActionVerb(q,"send","reply","compose","draft","write","mail","அனுப்பு","பதில்","எழுது"))
        return ActionRoute(ActionKind.EMAIL,text,localFallback=true)

    if(containsAny(q,"sms","text message","message","எஸ்எம்எஸ்","செய்தி") &&
        hasActionVerb(q,"send","text","message","reply","அனுப்பு","செய்தி","பதில்"))
        return ActionRoute(ActionKind.SMS,text,localFallback=true)

    if(containsAny(q,"calendar","meeting","appointment","event","காலெண்டர்","மீட்டிங்") &&
        hasActionVerb(q,"schedule","create","add","book","set","சேர்","உருவாக்கு","பதிவு"))
        return ActionRoute(ActionKind.CALENDAR,text,localFallback=true)

    if(containsAny(q,"google drive"," drive ","டிரைவ்") &&
        hasActionVerb(q,"upload","save","create","store","பதிவேற்று","சேமி"))
        return ActionRoute(ActionKind.DRIVE,text)

    if((containsAny(q,"github","apk","workflow","கிட்ஹப்")) &&
        hasActionVerb(q,"push","update","commit","run","trigger","build","release","create","பில்ட்","உருவாக்கு"))
        return ActionRoute(ActionKind.GITHUB,text)

    if(containsAny(q,"instagram","இன்ஸ்டாகிராம்") &&
        hasActionVerb(q,"post","publish","share","upload","போஸ்ட்","பகிர்","பதிவேற்று"))
        return ActionRoute(ActionKind.INSTAGRAM,text,localFallback=true)
    if(containsAny(q,"facebook","பேஸ்புக்") &&
        hasActionVerb(q,"post","publish","share","upload","போஸ்ட்","பகிர்","பதிவேற்று"))
        return ActionRoute(ActionKind.FACEBOOK,text,localFallback=true)
    if(containsAny(q,"linkedin","லிங்க்ட்இன்") &&
        hasActionVerb(q,"post","publish","share","upload","போஸ்ட்","பகிர்","பதிவேற்று"))
        return ActionRoute(ActionKind.LINKEDIN,text,localFallback=true)

    if(containsAny(q,"tally","டாலி") &&
        hasActionVerb(q,"post","create","make","prepare","enter","record","invoice","voucher","உருவாக்கு","பதிவு"))
        return ActionRoute(ActionKind.TALLY,text)

    if(
        containsAny(q,"tour lead","travel lead","டூர் லீட்") ||
        (containsAny(q,"tour","டூர்","சுற்றுலா") && containsAny(q,"quotation","itinerary","package","கோட்டேஷன்","பேக்கேஜ்","பயணத்திட்டம்"))
    ) return ActionRoute(ActionKind.TOUR_MISSION,text)

    if(
        Regex("""\b(?:research|compare|verify|find leads?|market research|competitor research)\b""",RegexOption.IGNORE_CASE)
            .containsMatchIn(q) || containsAny(q,"ஆராய்ச்சி","ஒப்பிடு","சரிபார்","லீட்ஸ் தேடு")
    ) return ActionRoute(ActionKind.RESEARCH_MISSION,text)

    if(
        containsAny(q,"pdf","document","docx","quotation","proposal","report","ஆவணம்","கோட்டேஷன்","ரிப்போர்ட்") &&
        hasActionVerb(q,"create","prepare","make","generate","draft","write","உருவாக்கு","தயார்","எழுது")
    ) return ActionRoute(ActionKind.DOCUMENT_MISSION,text)

    if(
        containsAny(q,"seo","marketing","campaign","promotion","social media","மார்க்கெட்டிங்","ப்ரமோஷன்") &&
        hasActionVerb(q,"create","prepare","run","plan","make","research","improve","உருவாக்கு","தயார்","மேம்படுத்து")
    ) return ActionRoute(ActionKind.MARKETING_MISSION,text)

    return null
}

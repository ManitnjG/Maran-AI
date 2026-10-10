package ai.maran.app.ui

import org.junit.Assert.*
import org.junit.Test

class ActionRoutingTest {
    @Test fun whatsappSendNeverFallsThroughToChat() {
        assertEquals(ActionKind.WHATSAPP,actionCommandRoute("send message in WhatsApp")?.kind)
        assertEquals(ActionKind.WHATSAPP,actionCommandRoute("Maran, send WhatsApp message to +919876543210 saying hello")?.kind)
        assertEquals(ActionKind.WHATSAPP,actionCommandRoute("மாரன் வாட்ஸ்அப்பில் செய்தி அனுப்பு")?.kind)
        assertNull(actionCommandRoute("Explain what WhatsApp is"))
        assertNull(actionCommandRoute("Open WhatsApp"))
    }

    @Test fun communicationActionsRouteDeterministically() {
        assertEquals(ActionKind.EMAIL,actionCommandRoute("send email to test@example.com")?.kind)
        assertEquals(ActionKind.SMS,actionCommandRoute("send SMS to +919876543210")?.kind)
        assertEquals(ActionKind.CALENDAR,actionCommandRoute("schedule a meeting in calendar")?.kind)
    }

    @Test fun externalConnectorActionsRouteToAgent() {
        assertEquals(ActionKind.DRIVE,actionCommandRoute("upload this to Google Drive")?.kind)
        assertEquals(ActionKind.GITHUB,actionCommandRoute("build APK using GitHub workflow")?.kind)
        assertEquals(ActionKind.INSTAGRAM,actionCommandRoute("post on Instagram saying new tour")?.kind)
        assertEquals(ActionKind.FACEBOOK,actionCommandRoute("publish on Facebook saying hello")?.kind)
        assertEquals(ActionKind.LINKEDIN,actionCommandRoute("post on LinkedIn saying update")?.kind)
        assertEquals(ActionKind.TALLY,actionCommandRoute("create Tally invoice")?.kind)
    }

    @Test fun workerStyleTasksRouteToMission() {
        assertEquals(ActionKind.TOUR_MISSION,actionCommandRoute("prepare a Tamil Nadu tour quotation")?.kind)
        assertEquals(ActionKind.RESEARCH_MISSION,actionCommandRoute("find leads for corporate tours")?.kind)
        assertEquals(ActionKind.DOCUMENT_MISSION,actionCommandRoute("create a PDF report")?.kind)
        assertEquals(ActionKind.MARKETING_MISSION,actionCommandRoute("prepare a marketing campaign")?.kind)
    }

    @Test fun normalQuestionsStayWithChat() {
        assertNull(actionCommandRoute("What is the capital of Tamil Nadu?"))
        assertNull(actionCommandRoute("Explain email security"))
        assertNull(actionCommandRoute("What is Instagram marketing?"))
    }
}

package ai.maran.app.ui

import org.junit.Assert.*
import org.junit.Test

class VoiceRoutingTest {
    @Test fun androidDeviceCommandsStayLocal() {
        assertEquals("device" to "camera",realWorldCommand("Open Camera"))
        assertEquals("device" to "maps",realWorldCommand("Open Maps"))
        assertEquals("device" to "wifi",realWorldCommand("Open Wi-Fi Settings"))
        assertEquals("device" to "camera",realWorldCommand("take a photo"))
    }

    @Test fun launchableAppNamesAreExactAndLocal() {
        assertEquals("app" to "spotify",realWorldCommand("Open Spotify"))
        assertEquals("app" to "கேமரா",realWorldCommand("Open கேமரா"))
        assertEquals("web" to "tour news",realWorldCommand("Search the web for tour news"))
        assertNull(realWorldCommand("Explain photosynthesis"))
    }

    @Test fun screenCommandsAreExplicit() {
        assertTrue(screenCommand("Maran, scroll down"))
        assertTrue(screenCommand("read this screen"))
        assertTrue(screenCommand("tap Settings"))
        assertFalse(screenCommand("What is the weather"))
    }

    @Test fun offlineCommandsAreUnaffected() {
        assertEquals("torch.on",LocalCommandEngine.parse("Maran, turn on flashlight")?.toolId)
        assertEquals(50,LocalCommandEngine.parse("Volume 50%")?.value)
    }
}

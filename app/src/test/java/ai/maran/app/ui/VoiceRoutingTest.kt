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
        assertTrue(screenCommand("show recent apps"))
        assertFalse(screenCommand("What is the weather"))
    }

    @Test fun inventoryRequestsUseLocalNativeTool() {
        assertTrue(DeviceInventoryCommand.matches("list all app i have"))
        assertTrue(DeviceInventoryCommand.matches("Maran, list all installed apps"))
        assertTrue(DeviceInventoryCommand.matches("Show me my apps"))
        assertTrue(DeviceInventoryCommand.matches("What apps do I have?"))
        assertTrue(DeviceInventoryCommand.matches("Which applications are installed on my phone?"))
        assertFalse(DeviceInventoryCommand.matches("show recent apps"))
        assertFalse(DeviceInventoryCommand.matches("show app settings"))
        assertFalse(DeviceInventoryCommand.matches("open WhatsApp"))
        assertFalse(DeviceInventoryCommand.matches("Explain Android apps"))
    }

    @Test fun offlineCommandsAreUnaffected() {
        assertEquals("torch.on",LocalCommandEngine.parse("Maran, turn on flashlight")?.toolId)
        assertEquals(50,LocalCommandEngine.parse("Volume 50%")?.value)
    }
}

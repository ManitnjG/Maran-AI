package ai.maran.app.ui

import org.junit.Assert.*
import org.junit.Test

class LocalCommandEngineTest {
    @Test fun torchCommandsRunLocally() {
        assertEquals("torch.on", LocalCommandEngine.parse("Maran, turn on flashlight")?.toolId)
        assertEquals("torch.off", LocalCommandEngine.parse("flashlight off")?.toolId)
        assertEquals("torch.on", LocalCommandEngine.parse("torch on")?.toolId)
    }
    @Test fun volumeRangeIsPassedToValidatedTool() {
        assertEquals(50, LocalCommandEngine.parse("Volume 50%")?.value)
        assertEquals(101, LocalCommandEngine.parse("set volume to 101%")?.value)
    }
    @Test fun timerNormalizesToSeconds() {
        assertEquals(600, LocalCommandEngine.parse("Set timer for 10 minutes")?.value)
        assertEquals(3600, LocalCommandEngine.parse("set timer for 1 hour")?.value)
    }
    @Test fun unknownRequestsGoToOtherRouters() {
        assertNull(LocalCommandEngine.parse("Explain quantum physics"))
        assertNull(LocalCommandEngine.parse("Call Ravi"))
    }
}

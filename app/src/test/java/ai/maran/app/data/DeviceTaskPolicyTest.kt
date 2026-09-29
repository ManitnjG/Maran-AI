package ai.maran.app.data

import org.junit.Assert.*
import org.junit.Test

class DeviceTaskPolicyTest {
    @Test fun emulatorDefaultsDoNotRequireAServerOnAPhone() {
        listOf("", "http://10.0.2.2:8000/", "http://localhost:8000", "http://127.0.0.1", "bad address").forEach {
            assertTrue(it,DeviceTaskPolicy.defaultDeviceMode(it))
        }
        assertFalse(DeviceTaskPolicy.defaultDeviceMode("https://maran.example.com/"))
    }
    @Test fun existingCustomServerIsPreserved() {
        assertFalse(DeviceTaskPolicy.defaultDeviceMode("http://192.168.1.2:8000/"))
    }
    @Test fun assignsTheWorkerWhoseSkillsMatchTheTask() {
        val workers=listOf(WorkerDto("1","Nila",listOf("content","social"),emptyList()),WorkerDto("2","Arjun",listOf("code","automation"),emptyList()))
        assertEquals("Arjun",DeviceTaskPolicy.chooseWorker("Write code for automation",workers)?.name)
        assertNull(DeviceTaskPolicy.chooseWorker("Task",emptyList()))
    }
    @Test fun processRestartDoesNotClaimUnfinishedWorkIsRunningOrComplete() {
        val m=RemoteMission("1","Draft report","running","Pending",listOf("Meera"),listOf(PlanStep("1","Draft","Meera",status="running")))
        val recovered=DeviceTaskPolicy.recover(m)
        assertEquals("interrupted",recovered.status)
        assertEquals("interrupted",recovered.plan.first().status)
        assertNotNull(recovered.plan.first().error)
        val completed=m.copy(status="completed")
        assertEquals(completed,DeviceTaskPolicy.recover(completed))
    }
}

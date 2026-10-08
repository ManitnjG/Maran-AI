package ai.maran.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.maran.app.ui.MaranApp

class MainActivity : ComponentActivity() {
    private var openTab by mutableStateOf<String?>(null)
    private var focusMissionId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTab=intent?.getStringExtra("open_tab")
        focusMissionId=intent?.getStringExtra("mission_id")
        enableEdgeToEdge()
        setContent { MaranApp(initialTab=openTab,focusMissionId=focusMissionId) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTab=intent.getStringExtra("open_tab")
        focusMissionId=intent.getStringExtra("mission_id")
    }
}

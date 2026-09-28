package ai.maran.app.ui
import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.maran.app.voice.WakeWordController

@Composable fun WakeControl(language:String,onCommand:(String)->Unit){
 val context=LocalContext.current
 val lifecycle=LocalLifecycleOwner.current
 var enabled by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("Microphone off")}
 val callback by rememberUpdatedState(onCommand)
 val controller=remember{if(SpeechRecognizer.isRecognitionAvailable(context))WakeWordController(context,{callback(it)},{status=it})else null}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->enabled=granted;if(granted)controller?.start(language)else status="Microphone permission denied"}
 DisposableEffect(controller,lifecycle){
  val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){enabled=false;controller?.stop()}}
  lifecycle.lifecycle.addObserver(observer)
  onDispose{lifecycle.lifecycle.removeObserver(observer);controller?.destroy()}
 }
 Text("Listen for ‘Maran’ on this screen")
 Switch(checked=enabled,onCheckedChange={on->
  if(!on){enabled=false;controller?.stop()}
  else if(controller==null){status="Speech recognition unavailable"}
  else if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){enabled=true;controller.start(language)}
  else permission.launch(Manifest.permission.RECORD_AUDIO)
 })
 Text(status,style=MaterialTheme.typography.bodySmall)
 Text("Uses your device speech service. Listening stops when this screen closes or the app goes into the background.",style=MaterialTheme.typography.bodySmall)
}

package ai.maran.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.maran.app.control.MaranAccessibilityService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ai.maran.app.data.SalesVoucherRequest
import java.time.LocalDate

@Composable fun ToolsScreen(state:MaranUiState,vm:MaranViewModel){
 val context=LocalContext.current
 val owner=LocalLifecycleOwner.current
 var deviceControlEnabled by remember { mutableStateOf(MaranAccessibilityService.available()) }
 DisposableEffect(owner) {
  val observer=LifecycleEventObserver { _,event ->
   if(event==Lifecycle.Event.ON_RESUME) deviceControlEnabled=MaranAccessibilityService.available()
  }
  owner.lifecycle.addObserver(observer)
  onDispose { owner.lifecycle.removeObserver(observer) }
 }
 val scope=rememberCoroutineScope()
 var fileStatus by remember{mutableStateOf<String?>(null)}
 var installedApps by remember { mutableStateOf<String?>(null) }
 var pendingExport by remember{mutableStateOf<ai.maran.app.data.ExportResult?>(null)}
 val saveFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
  val content=pendingExport?.content
  if(uri!=null&&content!=null)scope.launch{
   fileStatus=try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(content)}?:error("No destination")};"File saved"}catch(_:Exception){"Could not save file"}
  }
 }
 val restoreFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null)scope.launch{
   try{
    val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=2_000_000);out.write(buffer,0,n)};out.toString("UTF-8")}?:error("No file")}
    vm.restore(text)
   }catch(_:Exception){fileStatus="Cannot read backup (maximum 2 MB)"}
  }
 }
 var company by remember{mutableStateOf("")}
 var customer by remember{mutableStateOf("")}
 var ledger by remember{mutableStateOf("Sales")}
 var number by remember{mutableStateOf("")}
 var date by remember{mutableStateOf(LocalDate.now().toString())}
 var amount by remember{mutableStateOf("")}
 var memoryKey by remember{mutableStateOf("")}
 var memoryValue by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  MaranSectionHeading("Autonomous agent runtime","Safe work can continue through bounded background cycles")
  Text("MARAN plans, assigns workers, executes supported tools, observes results, verifies outputs, retries eligible failures, and learns reusable workflows. Consequential actions still require approval. OTP, CAPTCHA, passwords, PINs, CVV, biometrics and security prompts are never automated.",style=MaterialTheme.typography.bodySmall)
  Text("Learned workflows: "+state.learnedSkills.size,style=MaterialTheme.typography.bodySmall)
  Text("Background continuation uses Android WorkManager and runs only when network access is available.",style=MaterialTheme.typography.bodySmall)

  MaranSectionHeading("Workspace memory","Preferences and reusable context only — do not store secrets")
  MaranInput(memoryKey,{memoryKey=it},label="Memory key",modifier=Modifier.fillMaxWidth())
  MaranInput(memoryValue,{memoryValue=it},label="Memory value",modifier=Modifier.fillMaxWidth(),maxLines=4)
  MaranPrimaryButton(label="Remember",onClick={vm.saveMemory(memoryKey,memoryValue);memoryKey="";memoryValue=""},enabled=memoryKey.isNotBlank()&&memoryValue.isNotBlank())
  state.memory.forEach{item->
   MaranPanel(Modifier.fillMaxWidth()){
    Text(item.key,style=MaterialTheme.typography.titleSmall)
    Text(item.value,style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={vm.forgetMemory(item.key)}){Text("Forget")}
   }
  }

  MaranSectionHeading("Voice Device Control","Manage Android permissions safely")
  Text(if(deviceControlEnabled) "Device Control is enabled" else "Device Control is disabled")
  Text("Optional screen reading, Back, Home, scrolling and exact-label tapping. Enable MARAN Device Control manually in Android Accessibility settings. Do not use it to approve payments, authentication or security prompts.",style=MaterialTheme.typography.bodySmall)
  Text("If Android shows Restricted setting: open MARAN App Info, tap the three-dot menu (if available), choose Allow restricted settings, and approve with your screen lock. Only do this for an APK you trust. Then enable MARAN Device Control in Accessibility settings. MARAN cannot bypass this Android protection.",style=MaterialTheme.typography.bodySmall)
  OutlinedButton(onClick={
    try {
     context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package",context.packageName,null)))
    } catch(_:Exception) { fileStatus="Could not open MARAN App Info" }
  }) { Text("1. Open MARAN App Info") }
  OutlinedButton(onClick={
    try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    catch(_:Exception) { fileStatus="Accessibility settings unavailable on this phone" }
  }) { Text("2. Open Accessibility settings") }

  MaranSectionHeading("Apps on this phone")
  Text("This reads visible launchable apps directly from Android. No AI key or Accessibility permission is required, and the list is not sent to OpenRouter.",style=MaterialTheme.typography.bodySmall)
  MaranSecondaryButton(label="List installed apps",onClick={installedApps=DeviceAppInventory.listLaunchable(context)})
  installedApps?.let { result ->
   androidx.compose.foundation.text.selection.SelectionContainer {
    Text(result,style=MaterialTheme.typography.bodySmall)
   }
  }

  MaranSectionHeading("Readiness")
  state.capabilities?.let{c->
   Text("Models: "+c.models.ifEmpty{listOf("None configured")}.joinToString())
   Text("Storage: "+c.storage)
   c.features.forEach{(name,status)->Text(name.replace('_',' ')+": "+status.replace('_',' '))}
   if(c.integrations.isNotEmpty()){
    Spacer(Modifier.height(6.dp))
    Text("External actions",style=MaterialTheme.typography.titleMedium)
    c.integrations.forEach{(name,cap)->
     val suffix=if(cap.approval)" • approval required" else ""
     Text(name.replace('_',' ')+": "+cap.status.replace('_',' ')+suffix,style=MaterialTheme.typography.bodySmall)
    }
   }
   Text(c.note,style=MaterialTheme.typography.bodySmall)
  }
  MaranPrimaryButton(label="Test connections",onClick={vm.checkConnections()},enabled=!state.busy)
  state.diagnostics?.let{Text(it)}
  MaranSectionHeading("Saved work")
  MaranSecondaryButton(label="Export mission backup",onClick={vm.backup()},enabled=!state.busy)
  MaranSecondaryButton(label="Restore mission backup",onClick={restoreFile.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!state.busy)
  fileStatus?.let{Text(it)}
  MaranSectionHeading("Tally sales voucher draft")
  Text("For review and manual import. Enter exact existing company and ledger names. Tax handling, GST filing and Tally posting are not included.")
  MaranInput(company,{company=it},label="Company",modifier=Modifier.fillMaxWidth())
  MaranInput(customer,{customer=it},label="Customer ledger",modifier=Modifier.fillMaxWidth())
  MaranInput(ledger,{ledger=it},label="Sales ledger",modifier=Modifier.fillMaxWidth())
  MaranInput(number,{number=it},label="Voucher number",modifier=Modifier.fillMaxWidth())
  MaranInput(date,{date=it},label="Date YYYY-MM-DD",modifier=Modifier.fillMaxWidth())
  MaranInput(amount,{amount=it},label="Amount INR",modifier=Modifier.fillMaxWidth())
  MaranPrimaryButton(label="Prepare XML for review",onClick={vm.voucher(SalesVoucherRequest(company,customer,ledger,number,date,amount))},enabled=!state.busy&&listOf(company,customer,ledger,number,date,amount).all{it.isNotBlank()})
  state.export?.let{export->
   Text(export.filename,style=MaterialTheme.typography.titleMedium)
   export.note?.let{Text(it)}
   MaranPrimaryButton(label="Save file",onClick={pendingExport=export;saveFile.launch(export.filename)})
   OutlinedButton(enabled=export.content.length<=20000,onClick={
    val send=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,export.filename);putExtra(Intent.EXTRA_TEXT,export.content)}
    context.startActivity(Intent.createChooser(send,"Share export"))
   }){Text("Share export text")}
   androidx.compose.foundation.text.selection.SelectionContainer{Text(export.content.take(3000),style=MaterialTheme.typography.bodySmall)}
  }
 }
}

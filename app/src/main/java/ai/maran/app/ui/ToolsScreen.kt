package ai.maran.app.ui

import android.content.Intent
import android.provider.Settings
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
 val scope=rememberCoroutineScope()
 var fileStatus by remember{mutableStateOf<String?>(null)}
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
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Voice Device Control",style=MaterialTheme.typography.titleLarge)
  Text(if(MaranAccessibilityService.available()) "Device Control is enabled" else "Device Control is disabled")
  Text("Optional screen reading, Back, Home, scrolling and exact-label tapping. Enable MARAN Device Control manually in Android Accessibility settings. Do not use it to approve payments, authentication or security prompts.",style=MaterialTheme.typography.bodySmall)
  OutlinedButton(onClick={
    try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    catch(_:Exception) { fileStatus="Accessibility settings unavailable on this phone" }
  }) { Text("Open Accessibility settings") }

  Text("Readiness",style=MaterialTheme.typography.titleLarge)
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
  Button(onClick={vm.checkConnections()},enabled=!state.busy){Text("Test connections")}
  state.diagnostics?.let{Text(it)}
  Text("Saved work",style=MaterialTheme.typography.titleLarge)
  OutlinedButton(onClick={vm.backup()},enabled=!state.busy){Text("Export mission backup")}
  OutlinedButton(onClick={restoreFile.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!state.busy){Text("Restore mission backup")}
  fileStatus?.let{Text(it)}
  Text("Tally sales voucher draft",style=MaterialTheme.typography.titleLarge)
  Text("For review and manual import. Enter exact existing company and ledger names. Tax handling, GST filing and Tally posting are not included.")
  OutlinedTextField(company,{company=it},label={Text("Company")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(customer,{customer=it},label={Text("Customer ledger")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(ledger,{ledger=it},label={Text("Sales ledger")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(number,{number=it},label={Text("Voucher number")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(date,{date=it},label={Text("Date YYYY-MM-DD")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(amount,{amount=it},label={Text("Amount INR")},modifier=Modifier.fillMaxWidth())
  Button(onClick={vm.voucher(SalesVoucherRequest(company,customer,ledger,number,date,amount))},enabled=!state.busy&&listOf(company,customer,ledger,number,date,amount).all{it.isNotBlank()}){Text("Prepare XML for review")}
  state.export?.let{export->
   Text(export.filename,style=MaterialTheme.typography.titleMedium)
   export.note?.let{Text(it)}
   Button(onClick={pendingExport=export;saveFile.launch(export.filename)}){Text("Save file")}
   OutlinedButton(enabled=export.content.length<=20000,onClick={
    val send=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,export.filename);putExtra(Intent.EXTRA_TEXT,export.content)}
    context.startActivity(Intent.createChooser(send,"Share export"))
   }){Text("Share export text")}
   androidx.compose.foundation.text.selection.SelectionContainer{Text(export.content.take(3000),style=MaterialTheme.typography.bodySmall)}
  }
 }
}

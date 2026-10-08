package ai.maran.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.OpenableColumns
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
 var memoryKey by remember{mutableStateOf("")}
 var memoryValue by remember{mutableStateOf("")}
 var allowKnowledgeAi by remember{mutableStateOf(false)}
 var notificationsAllowed by remember{
  mutableStateOf(Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
 }
 val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
  notificationsAllowed=granted
 }
 val knowledgeFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null)scope.launch{
   try{
    val name=withContext(Dispatchers.IO){
     context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
      if(cursor.moveToFirst())cursor.getString(0) else null
     } ?: (uri.lastPathSegment ?: "knowledge-file")
    }
    val mime=context.contentResolver.getType(uri) ?: "application/octet-stream"
    val bytes=withContext(Dispatchers.IO){
     context.contentResolver.openInputStream(uri)?.use{input->
      val out=java.io.ByteArrayOutputStream()
      val buffer=ByteArray(8192)
      var total=0
      while(true){
       val n=input.read(buffer);if(n<0)break
       total+=n;require(total<=5_000_000)
       out.write(buffer,0,n)
      }
      out.toByteArray()
     } ?: error("No file")
    }
    vm.uploadKnowledge(name,mime,bytes,allowKnowledgeAi)
    fileStatus="Knowledge file queued for upload"
   }catch(_:Exception){fileStatus="Cannot read knowledge file (maximum 5 MB)"}
  }
 } Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  MaranSectionHeading("Autonomous agent runtime","Safe work can continue through bounded background cycles")
  Text("MARAN plans, assigns workers, executes supported tools, observes results, verifies outputs, retries eligible failures, and learns reusable workflows. Consequential actions still require approval. OTP, CAPTCHA, passwords, PINs, CVV, biometrics and security prompts are never automated.",style=MaterialTheme.typography.bodySmall)
  Text("Learned workflows: "+state.learnedSkills.size,style=MaterialTheme.typography.bodySmall)
  Text("Background continuation uses Android WorkManager and runs only when network access is available.",style=MaterialTheme.typography.bodySmall)

  MaranSectionHeading("Mission notifications","Get approval, completion and attention alerts")
  Text(if(notificationsAllowed)"Notifications are enabled" else "Notifications are off",style=MaterialTheme.typography.bodySmall)
  if(!notificationsAllowed && Build.VERSION.SDK_INT>=33){
   MaranPrimaryButton("Enable notifications",onClick={notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)})
  } else {
   Text("MARAN only notifies for mission approval, completion, blocked or failed states.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }

  MaranSectionHeading("Knowledge files","Your selected reference files; AI use is opt-in per upload")
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
   Column(Modifier.weight(1f)){
    Text("Allow AI context for this upload",style=MaterialTheme.typography.bodyMedium)
    Text("Only matching snippets from files you explicitly allow can be inserted into worker prompts.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
   Switch(checked=allowKnowledgeAi,onCheckedChange={allowKnowledgeAi=it})
  }
  MaranSecondaryButton("Add knowledge file",onClick={
   knowledgeFile.launch(arrayOf("application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document","text/*","application/json","text/csv"))
  })
  state.knowledge.forEach{doc->
   MaranPanel(Modifier.fillMaxWidth()){
    Text(doc.name,style=MaterialTheme.typography.titleSmall)
    Text((doc.characters/1000.0).let{String.format("%.1fK characters",it)}+" • "+if(doc.allow_ai)"AI context allowed" else "Search only",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick={vm.deleteKnowledge(doc.id)}){Text("Remove file")}
   }
  }

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

  DistributionDeviceControls { fileStatus=it }

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

package ai.maran.app.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val PUTER_MODEL = "openai/gpt-5.4-nano"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AiChatScreen() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadDataWithBaseURL("https://puter.com/", puterChatHtml(), "text/html", "UTF-8", null)
            }
        }
    )
}

private fun puterChatHtml(): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<script src="https://js.puter.com/v2/"></script>
<style>
:root{color-scheme:light dark}
*{box-sizing:border-box} body{margin:0;font-family:system-ui,-apple-system,sans-serif;background:#111318;color:#f5f5f5}
main{max-width:900px;margin:auto;padding:18px;min-height:100vh;display:flex;flex-direction:column;gap:12px}
h1{font-size:28px;margin:4px 0}.sub{opacity:.72;font-size:13px}.badge{display:inline-block;padding:6px 10px;border-radius:999px;background:#1f2937;font-size:12px}
#chat{display:flex;flex-direction:column;gap:10px;flex:1;min-height:50vh}
.msg{padding:12px 14px;border-radius:16px;white-space:pre-wrap;line-height:1.45}.user{background:#243447;margin-left:12%}.ai{background:#1c1f26;margin-right:8%}
textarea{width:100%;min-height:86px;border-radius:14px;padding:12px;font:inherit;background:#171a20;color:inherit;border:1px solid #3b404a}
.actions{display:flex;gap:8px}button{border:0;border-radius:12px;padding:12px 16px;font-weight:700}.primary{background:#8ab4f8;color:#08111f}.secondary{background:#282d36;color:#fff}
#error{color:#ff8a80;font-size:13px}.status{font-size:12px;opacity:.68}
</style>
</head>
<body><main>
<div>
  <h1>MARAN AI</h1>
  <div class="badge">Free keyless AI • openai/gpt-5.4-nano</div>
  <div class="sub">No OpenCode key or model API key is stored in MARAN. Puter may ask you to sign in so it can authorize your own usage.</div>
</div>
<div id="chat"><div class="msg ai">Hi. Ask me in English or தமிழ்.</div></div>
<div id="error"></div><div class="status" id="status">Ready</div>
<textarea id="prompt" placeholder="Ask MARAN AI…"></textarea>
<div class="actions"><button class="primary" id="send">Send</button><button class="secondary" id="clear">Clear</button></div>
</main>
<script>
const chat=document.getElementById("chat"), promptBox=document.getElementById("prompt"), send=document.getElementById("send"), clear=document.getElementById("clear"), error=document.getElementById("error"), status=document.getElementById("status");
let history=[];
function add(role,text){const d=document.createElement("div");d.className="msg "+(role==="user"?"user":"ai");d.textContent=text;chat.appendChild(d);window.scrollTo(0,document.body.scrollHeight);}
send.onclick=async()=>{
 const text=promptBox.value.trim(); if(!text||send.disabled)return;
 promptBox.value=""; error.textContent=""; send.disabled=true; status.textContent="Thinking…"; add("user",text);
 try{
   const context=history.slice(-8).map(x=>(x.role==="user"?"User: ":"Assistant: ")+x.text).join("\\n")+(history.length?"\\n":"")+"User: "+text;
   const response=await puter.ai.chat(context,{model:"openai/gpt-5.4-nano"});
   const answer=typeof response==="string"?response:(response?.message?.content||response?.text||String(response));
   history.push({role:"user",text},{role:"assistant",text:answer}); add("assistant",answer); status.textContent="Answered by openai/gpt-5.4-nano";
 }catch(e){error.textContent="AI request failed: "+(e?.message||e);status.textContent="Ready";}
 finally{send.disabled=false;}
};
clear.onclick=()=>{history=[];chat.innerHTML="<div class=\"msg ai\">Chat cleared. Ask me anything.</div>";error.textContent="";status.textContent="Ready";};
promptBox.addEventListener("keydown",e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();send.click();}});
</script></body></html>
""".trimIndent()
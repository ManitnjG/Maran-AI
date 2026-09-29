package ai.maran.app.ui

import android.annotation.SuppressLint
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
            val container = FrameLayout(context)
            val main = WebView(context)

            fun configure(webView: WebView) {
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.databaseEnabled = true
                webView.settings.javaScriptCanOpenWindowsAutomatically = true
                webView.settings.setSupportMultipleWindows(true)
                webView.settings.allowFileAccess = false
                webView.settings.allowContentAccess = false
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                webView.webViewClient = WebViewClient()
            }

            configure(main)
            container.addView(
                main,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            main.webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    if (!isUserGesture || resultMsg == null) return false
                    val popup = WebView(context)
                    configure(popup)
                    popup.webChromeClient = object : WebChromeClient() {
                        override fun onCloseWindow(window: WebView?) {
                            window?.let {
                                container.removeView(it)
                                it.destroy()
                            }
                        }
                    }
                    container.addView(
                        popup,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                    transport.webView = popup
                    resultMsg.sendToTarget()
                    return true
                }
            }

            main.loadDataWithBaseURL(
                "https://maran-ai.local/",
                puterChatHtml(),
                "text/html",
                "UTF-8",
                null
            )
            container
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
*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,sans-serif;background:#111318;color:#f5f5f5}
main{max-width:900px;margin:auto;padding:18px;min-height:100vh;display:flex;flex-direction:column;gap:12px}
h1{font-size:28px;margin:4px 0}.sub{opacity:.72;font-size:13px}.badge{display:inline-block;padding:6px 10px;border-radius:999px;background:#1f2937;font-size:12px}
#chat{display:flex;flex-direction:column;gap:10px;flex:1;min-height:42vh}
.msg{padding:12px 14px;border-radius:16px;white-space:pre-wrap;line-height:1.45}.user{background:#243447;margin-left:12%}.ai{background:#1c1f26;margin-right:8%}
textarea{width:100%;min-height:86px;border-radius:14px;padding:12px;font:inherit;background:#171a20;color:inherit;border:1px solid #3b404a}
.actions{display:flex;gap:8px;flex-wrap:wrap}button{border:0;border-radius:12px;padding:12px 16px;font-weight:700}.primary{background:#8ab4f8;color:#08111f}.secondary{background:#282d36;color:#fff}
#connect{background:#34a853;color:#06150a}#error{color:#ff8a80;font-size:13px}.status{font-size:12px;opacity:.72}
</style>
</head>
<body><main>
<div>
  <h1>MARAN AI</h1>
  <div class="badge">Free AI • __MODEL__</div>
  <div class="sub">No AI API key is required. The first use may open a Puter authorization window; you can use a temporary Puter user or sign in.</div>
</div>
<div class="actions"><button id="connect">Connect free AI</button></div>
<div id="chat"><div class="msg ai">Hi. Ask me in English or தமிழ்.</div></div>
<div id="error"></div><div class="status" id="status">Checking AI access…</div>
<textarea id="prompt" placeholder="Ask MARAN AI…"></textarea>
<div class="actions"><button class="primary" id="send">Send</button><button class="secondary" id="clear">Clear</button></div>
</main>
<script>
const chat=document.getElementById("chat"), promptBox=document.getElementById("prompt"),
send=document.getElementById("send"), clear=document.getElementById("clear"),
connect=document.getElementById("connect"), error=document.getElementById("error"),
status=document.getElementById("status");
let history=[];

function add(role,text){
  const d=document.createElement("div");
  d.className="msg "+(role==="user"?"user":"ai");
  d.textContent=text;
  chat.appendChild(d);
  window.scrollTo(0,document.body.scrollHeight);
}

function messageOf(e){
  return e?.message || e?.msg || e?.error || String(e || "Unknown error");
}

async function ensureAuth(){
  if(puter.authToken){
    connect.textContent="AI connected";
    status.textContent="Ready";
    return true;
  }
  status.textContent="Authorizing free AI…";
  error.textContent="";
  try{
    await puter.auth.signIn({attempt_temp_user_creation:true});
    if(!puter.authToken) throw new Error("Authorization did not complete");
    connect.textContent="AI connected";
    status.textContent="Ready";
    return true;
  }catch(e){
    error.textContent="AI authorization failed: "+messageOf(e);
    status.textContent="Tap Connect free AI and complete the authorization window.";
    connect.textContent="Connect free AI";
    return false;
  }
}

connect.onclick=async()=>{ await ensureAuth(); };

send.onclick=async()=>{
  const text=promptBox.value.trim();
  if(!text || send.disabled) return;
  error.textContent="";
  send.disabled=true;
  try{
    if(!(await ensureAuth())) return;
    promptBox.value="";
    status.textContent="Thinking…";
    add("user",text);
    const messages=history.slice(-8).map(x=>({role:x.role,content:x.text}));
    messages.push({role:"user",content:text});
    const response=await puter.ai.chat(messages,{model:"__MODEL__"});
    const answer=typeof response==="string"
      ? response
      : (response?.message?.content || response?.text || String(response));
    history.push({role:"user",text:text},{role:"assistant",text:answer});
    add("assistant",answer);
    status.textContent="Answered by __MODEL__";
  }catch(e){
    const msg=messageOf(e);
    if(String(msg).toLowerCase().includes("unauthorized")){
      connect.textContent="Reconnect free AI";
      status.textContent="Authorization expired. Tap Reconnect free AI.";
    } else {
      status.textContent="Ready";
    }
    error.textContent="AI request failed: "+msg;
  }finally{
    send.disabled=false;
  }
};

clear.onclick=()=>{
  history=[];
  chat.innerHTML='<div class="msg ai">Chat cleared. Ask me anything.</div>';
  error.textContent="";
  status.textContent=puter.authToken ? "Ready" : "Connect free AI to start";
};

promptBox.addEventListener("keydown",e=>{
  if(e.key==="Enter"&&!e.shiftKey){
    e.preventDefault();
    send.click();
  }
});

setTimeout(()=>{
  if(puter.authToken){
    connect.textContent="AI connected";
    status.textContent="Ready";
  }else{
    status.textContent="Tap Connect free AI once to authorize.";
  }
},500);
</script>
</body>
</html>
""".replace("__MODEL__", PUTER_MODEL)

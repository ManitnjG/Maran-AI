package ai.maran.app.data
import org.junit.Assert.*
import org.junit.Test

class OpenCodeTest {
 @Test fun excludesPaidUnknownAndDeprecatedPrices() {
  val models=FreeModelCatalog.parse("""{"opencode":{"npm":"@ai-sdk/openai-compatible","models":{
   "free":{"name":"Free","cost":{"input":0,"output":0}},
   "paid-free":{"cost":{"input":1,"output":0}},
   "unknown":{"cost":{}},
   "cache":{"cost":{"input":0,"output":0,"cache_read":1}},
   "old":{"status":"deprecated","cost":{"input":0,"output":0}},
   "responses":{"provider":{"npm":"@ai-sdk/openai"},"cost":{"input":0,"output":0}}
  }}}""")
  assertEquals(setOf("free","responses"),models.map { it.id }.toSet())
  assertEquals("responses",models.first { it.id=="responses" }.protocol)
 }
 @Test fun respectsAccessAndQuotaLimits() {
  listOf(401,402,403,429,400).forEach { assertFalse(FreeModelCatalog.mayFallback(it)) }
  listOf(404,502,503,504).forEach { assertTrue(FreeModelCatalog.mayFallback(it)) }
 }
 @Test fun parsesAllSupportedTextFormats() {
  assertEquals("hello",OpenCodeClient.parseReply("chat/completions","""{"choices":[{"message":{"content":"hello"}}]}"""))
  assertEquals("hello",OpenCodeClient.parseReply("responses","""{"output":[{"type":"reasoning"},{"content":[{"type":"output_text","text":"hello"}]}]}"""))
  assertEquals("hello",OpenCodeClient.parseReply("messages","""{"content":[{"type":"text","text":"hello"}]}"""))
  assertEquals("",OpenCodeClient.parseReply("chat/completions","""{"choices":[{"message":{"content":null}}]}"""))
 }
}

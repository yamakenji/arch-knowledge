package io.github.yamakenji.archknowledge.adapters.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.github.yamakenji.archknowledge.application.impact.ImpactIntent
import io.github.yamakenji.archknowledge.application.impact.ImpactInterpretationOutcome
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LangChain4jImpactIntentInterpreterTest {
    private fun interpreter(payload: String) = LangChain4jImpactIntentInterpreter(object : ChatModel {
        override fun doChat(request: ChatRequest): ChatResponse {
            assertEquals(2, request.messages().size)
            assertIs<SystemMessage>(request.messages()[0])
            assertEquals("user request", (request.messages()[1] as UserMessage).singleText())
            return ChatResponse.builder().aiMessage(AiMessage.from(payload)).build()
        }
    })

    @Test
    fun `accepts only exact string selectors`() {
        assertEquals(
            ImpactInterpretationOutcome.Interpreted(ImpactIntent("impact", "stable-id")),
            interpreter("""{"operation":"impact","startConceptId":"stable-id"}""").interpret("user request"),
        )
        assertEquals(
            ImpactInterpretationOutcome.Interpreted(ImpactIntent("impact", startConceptName = "Exact Name")),
            interpreter("""{"operation":"impact","startConceptName":"Exact Name"}""").interpret("user request"),
        )
        assertEquals(ImpactInterpretationOutcome.Unsupported,
            interpreter("""{"operation":"delete"}""").interpret("user request"))
    }

    @Test
    fun `rejects malformed and policy bearing output`() {
        val payloads = listOf(
            "", "not JSON", "[]", "null", "{}", "```json\n{}\n```",
            """{"operation":"impact"}""",
            """{"operation":"impact","startConceptId":""}""",
            """{"operation":"impact","startConceptId":null}""",
            """{"operation":1,"startConceptId":"x"}""",
            """{"operation":"impact","startConceptId":true}""",
            """{"operation":"impact","startConceptId":[]}""",
            """{"operation":"impact","startConceptId":{}}""",
            """{"operation":"impact","startConceptId":"x","startConceptName":"y"}""",
            """{"operation":"impact","startConceptId":"x","depth":2}""",
            """{"operation":"impact","startConceptId":"x","cypher":"MATCH (n) RETURN n"}""",
            """{"operation":"impact","operation":"delete","startConceptId":"x"}""",
            """{"operation":"impact","startConceptId":"x"} {}""",
        )
        payloads.forEach { payload ->
            assertEquals(ImpactInterpretationOutcome.Malformed, interpreter(payload).interpret("user request"), payload)
        }
    }

    @Test
    fun `provider exceptions are redacted`() {
        val adapter = LangChain4jImpactIntentInterpreter(object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse = error("secret provider body")
        })
        assertEquals(ImpactInterpretationOutcome.InfrastructureFailure, adapter.interpret("user request"))
        assertFalse(adapter.interpret("user request").toString().contains("secret"))
    }

    @Test
    fun `configured endpoint sends only instructions and user text and maps HTTP failure`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var body = ""
        var authorization = ""
        var status = 200
        server.createContext("/v1/chat/completions") { exchange ->
            body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            authorization = exchange.requestHeaders.getFirst("Authorization")
            val response = if (status == 200) {
                """{"id":"test","object":"chat.completion","created":0,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"{\"operation\":\"impact\",\"startConceptId\":\"test-id\"}"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
            } else "secret provider error"
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val config = OpenAiImpactConfiguration("http://127.0.0.1:${server.address.port}/v1/",
                "test-model", "test-only-key", Duration.ofSeconds(5))
            val adapter = LangChain4jImpactIntentInterpreter(config)
            assertEquals(ImpactInterpretationOutcome.Interpreted(ImpactIntent("impact", "test-id")),
                adapter.interpret("user request"))
            assertEquals("Bearer test-only-key", authorization)
            assertTrue(body.contains("user request"))
            assertTrue(body.contains("test-model"))
            assertFalse(body.contains("test-only-key"))
            val request = ObjectMapper().readTree(body)
            assertFalse(request.has("tools"))
            assertEquals(2, request.get("messages").size())
            assertEquals("system", request.get("messages")[0].get("role").textValue())
            assertEquals("user request", request.get("messages")[1].get("content").textValue())
            assertFalse(config.toString().contains("test-only-key"))
            status = 401
            assertEquals(ImpactInterpretationOutcome.InfrastructureFailure, adapter.interpret("user request"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `invalid configuration never reveals secrets`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            OpenAiImpactConfiguration("https://user:secret@example.com/v1", "model", "secret")
        }
        assertEquals("Invalid model endpoint", failure.message)
    }
}
package cg.creamgod45

import cg.creamgod45.localization.AiProviderType
import cg.creamgod45.localization.AiTranslationItemDto
import cg.creamgod45.localization.AiTranslationRequestDto
import cg.creamgod45.localization.AiTranslationSuggestionDto
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AiTranslationSupportTest {
    @Test
    fun `openai compatible request batches items and parses strict ids`() {
        val authorization = AtomicReference<String>()
        val requestBody = AtomicReference<String>()
        val server = server { exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"choices":[{"message":{"content":"{\"translations\":[{\"id\":\"item0\",\"value\":\"Hola {name}\"}]}"}}]}"""
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        try {
            val result = AiTranslationSupport.translate(
                request(AiProviderType.OPENAI_COMPATIBLE, server.address.port).copy(
                    previousSuggestions = listOf(AiTranslationSuggestionDto("item0", "Hola")),
                    userFeedback = "Use a formal tone",
                ),
            )
            assertEquals("Bearer test-token", authorization.get())
            assertContains(requestBody.get(), "item0")
            assertContains(requestBody.get(), "Hello {name}")
            assertContains(requestBody.get(), "previous_translations")
            assertContains(requestBody.get(), "Use a formal tone")
            assertFalse(requestBody.get().contains("\"temperature\""))
            assertEquals("Hola {name}", result.suggestions.single().translatedValue)
        } finally { server.stop(0) }
    }

    @Test
    fun `anthropic request uses required headers and content blocks`() {
        val apiKey = AtomicReference<String>()
        val version = AtomicReference<String>()
        val requestBody = AtomicReference<String>()
        val server = server { exchange ->
            apiKey.set(exchange.requestHeaders.getFirst("x-api-key"))
            version.set(exchange.requestHeaders.getFirst("anthropic-version"))
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"content":[{"type":"text","text":"{\"translations\":[{\"id\":\"item0\",\"value\":\"Bonjour {name}\"}]}"}]}"""
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        try {
            val result = AiTranslationSupport.translate(request(AiProviderType.ANTHROPIC, server.address.port))
            assertEquals("test-token", apiKey.get())
            assertEquals("2023-06-01", version.get())
            assertFalse(requestBody.get().contains("\"temperature\""))
            assertEquals("Bonjour {name}", result.suggestions.single().translatedValue)
        } finally { server.stop(0) }
    }

    @Test
    fun `configured temperature is sent and provider ranges are validated`() {
        val requestBody = AtomicReference<String>()
        val server = server { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"choices":[{"message":{"content":"{\"translations\":[{\"id\":\"item0\",\"value\":\"Hola\"}]}"}}]}"""
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        try {
            AiTranslationSupport.translate(request(AiProviderType.OPENAI_COMPATIBLE, server.address.port).copy(temperature = 1.0))
            assertContains(requestBody.get(), "\"temperature\":1.0")
        } finally { server.stop(0) }

        assertFailsWith<IllegalArgumentException> {
            AiTranslationSupport.validate(request(AiProviderType.OPENAI_COMPATIBLE, 443).copy(temperature = 2.1))
        }
        assertFailsWith<IllegalArgumentException> {
            AiTranslationSupport.validate(request(AiProviderType.ANTHROPIC, 443).copy(temperature = 1.1))
        }
    }

    @Test
    fun `rejects unsafe endpoint and mismatched response ids`() {
        assertFailsWith<IllegalArgumentException> {
            AiTranslationSupport.validate(request(AiProviderType.OPENAI_COMPATIBLE, 443).copy(endpoint = "http://example.com/v1/chat/completions"))
        }
        assertFailsWith<IllegalArgumentException> {
            AiTranslationSupport.parseSuggestions(request(AiProviderType.OPENAI_COMPATIBLE, 443), """{"translations":[{"id":"other","value":"x"}]}""")
        }
        assertFailsWith<IllegalArgumentException> {
            AiTranslationSupport.validate(
                request(AiProviderType.OPENAI_COMPATIBLE, 443).copy(
                    previousSuggestions = listOf(AiTranslationSuggestionDto("other", "x")),
                    userFeedback = "retry",
                ),
            )
        }
    }

    private fun request(provider: AiProviderType, port: Int) =
        AiTranslationRequestDto(
            provider = provider,
            endpoint = "http://127.0.0.1:$port/api",
            model = "test-model",
            apiToken = "test-token",
            sourceLocale = "en",
            targetLocale = "es",
            items = listOf(AiTranslationItemDto("item0", "messages", "welcome", "Hello {name}")),
        )

    private fun server(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api", handler)
            start()
        }
}

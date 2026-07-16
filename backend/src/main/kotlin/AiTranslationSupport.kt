package cg.creamgod45

import cg.creamgod45.localization.AiProviderType
import cg.creamgod45.localization.AiTranslationRequestDto
import cg.creamgod45.localization.AiTranslationResultDto
import cg.creamgod45.localization.AiTranslationSuggestionDto
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal object AiTranslationSupport {
    private const val MAX_ITEMS = 100
    private const val MAX_ITEM_CHARS = 10_000
    private const val MAX_TOTAL_CHARS = 60_000
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }
    private val client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    fun translate(request: AiTranslationRequestDto): AiTranslationResultDto {
        validate(request)
        val prompt = prompt(request)
        val httpRequest = buildHttpRequest(request, prompt)
        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
        val body = response.body().use { input ->
            val bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1)
            require(bytes.size <= MAX_RESPONSE_BYTES) { backendMessage("ai.response.too.large") }
            bytes.toString(Charsets.UTF_8)
        }
        require(response.statusCode() in 200..299) {
            backendMessage("ai.http.error", response.statusCode(), safeError(body))
        }
        return parseProviderResponse(request, body)
    }

    internal fun validate(request: AiTranslationRequestDto) {
        val uri = runCatching { URI(request.endpoint.trim()) }.getOrElse { error(backendMessage("ai.endpoint.invalid")) }
        require(uri.isAbsolute && uri.userInfo == null && uri.fragment == null && uri.query == null) { backendMessage("ai.endpoint.invalid") }
        require(uri.scheme.equals("https", true) || (uri.scheme.equals("http", true) && isLoopback(uri.host))) {
            backendMessage("ai.endpoint.https")
        }
        require(!uri.host.isNullOrBlank() && request.endpoint.length <= 2_048) { backendMessage("ai.endpoint.invalid") }
        require(request.model.isNotBlank() && request.model.length <= 200 && request.model.none(Char::isISOControl)) { backendMessage("ai.model.invalid") }
        require(request.apiToken.isNotBlank() && request.apiToken.length <= 4_096 && request.apiToken.none(Char::isISOControl)) { backendMessage("ai.token.invalid") }
        require(request.sourceLocale.matches(Regex("[A-Za-z0-9_-]{1,32}"))) { backendMessage("ai.source.locale.invalid") }
        require(request.targetLocale.matches(Regex("[A-Za-z0-9_-]{1,32}")) && request.sourceLocale != request.targetLocale) { backendMessage("ai.target.locale.invalid") }
        require(request.items.size in 1..MAX_ITEMS && request.items.distinctBy { it.id }.size == request.items.size) { backendMessage("ai.batch.invalid") }
        require(request.items.sumOf { it.sourceValue.length } <= MAX_TOTAL_CHARS) { backendMessage("ai.batch.too.large") }
        request.items.forEach {
            require(it.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && it.key.length in 1..512) { backendMessage("ai.item.invalid") }
            require(it.sourceValue.length in 1..MAX_ITEM_CHARS && it.sourceValue.none { char -> char == '\u0000' }) { backendMessage("ai.source.value.invalid") }
        }
        require(request.userFeedback.length <= 4_000 && request.userFeedback.none { it == '\u0000' }) { backendMessage("ai.feedback.invalid") }
        require(request.previousSuggestions.isEmpty() || request.previousSuggestions.map { it.id }.toSet() == request.items.map { it.id }.toSet()) {
            backendMessage("ai.feedback.context.invalid")
        }
    }

    internal fun buildHttpRequest(request: AiTranslationRequestDto, prompt: String): HttpRequest {
        val body =
            when (request.provider) {
                AiProviderType.OPENAI_COMPATIBLE ->
                    buildJsonObject {
                        put("model", JsonPrimitive(request.model))
                        put("temperature", JsonPrimitive(0.1))
                        put("messages", buildJsonArray {
                            add(message("system", systemPrompt()))
                            add(message("user", prompt))
                        })
                    }
                AiProviderType.ANTHROPIC ->
                    buildJsonObject {
                        put("model", JsonPrimitive(request.model))
                        put("max_tokens", JsonPrimitive(8_192))
                        put("temperature", JsonPrimitive(0.1))
                        put("system", JsonPrimitive(systemPrompt()))
                        put("messages", buildJsonArray { add(message("user", prompt)) })
                    }
            }.toString()
        return HttpRequest.newBuilder(URI(request.endpoint.trim()))
            .timeout(Duration.ofSeconds(90))
            .header("Content-Type", "application/json")
            .apply {
                when (request.provider) {
                    AiProviderType.OPENAI_COMPATIBLE -> header("Authorization", "Bearer ${request.apiToken}")
                    AiProviderType.ANTHROPIC -> {
                        header("x-api-key", request.apiToken)
                        header("anthropic-version", "2023-06-01")
                    }
                }
            }.POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
            .build()
    }

    internal fun parseProviderResponse(request: AiTranslationRequestDto, body: String): AiTranslationResultDto {
        val root = json.parseToJsonElement(body).jsonObject
        val content =
            when (request.provider) {
                AiProviderType.OPENAI_COMPATIBLE ->
                    root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                AiProviderType.ANTHROPIC ->
                    root["content"]?.jsonArray
                        ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
                        ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            } ?: error(backendMessage("ai.response.text.missing"))
        return parseSuggestions(request, content)
    }

    internal fun parseSuggestions(request: AiTranslationRequestDto, content: String): AiTranslationResultDto {
        val clean = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val items = json.parseToJsonElement(clean).jsonObject["translations"]?.jsonArray ?: error(backendMessage("ai.response.translations.missing"))
        val suggestions = items.map { element ->
            val item = element.jsonObject
            val id = item["id"]?.jsonPrimitive?.contentOrNull ?: error(backendMessage("ai.response.id.missing"))
            val value = item["value"]?.jsonPrimitive?.contentOrNull ?: error(backendMessage("ai.response.value.missing"))
            require(value.length <= MAX_ITEM_CHARS && value.none { it == '\u0000' }) { backendMessage("ai.response.value.invalid") }
            AiTranslationSuggestionDto(id, value)
        }
        require(suggestions.distinctBy { it.id }.size == suggestions.size) { backendMessage("ai.response.id.duplicate") }
        require(suggestions.map { it.id }.toSet() == request.items.map { it.id }.toSet()) { backendMessage("ai.response.id.mismatch") }
        return AiTranslationResultDto(suggestions)
    }

    private fun prompt(request: AiTranslationRequestDto): String =
        buildJsonObject {
            put("source_locale", JsonPrimitive(request.sourceLocale))
            put("target_locale", JsonPrimitive(request.targetLocale))
            put("items", buildJsonArray {
                request.items.forEach { item ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(item.id))
                        put("namespace", JsonPrimitive(item.namespace))
                        put("key", JsonPrimitive(item.key))
                        put("value", JsonPrimitive(item.sourceValue))
                    })
                }
            })
            if (request.previousSuggestions.isNotEmpty()) {
                put("previous_translations", buildJsonArray {
                    request.previousSuggestions.forEach { suggestion ->
                        add(buildJsonObject { put("id", JsonPrimitive(suggestion.id)); put("value", JsonPrimitive(suggestion.translatedValue)) })
                    }
                })
                put("user_feedback", JsonPrimitive(request.userFeedback))
            }
        }.toString()

    private fun systemPrompt() =
        "Translate localization values faithfully. Preserve placeholders, ICU/MessageFormat syntax, HTML, Markdown, escapes, line breaks, and leading/trailing whitespace. Never translate keys or IDs. Return only JSON: {\"translations\":[{\"id\":\"...\",\"value\":\"...\"}]} with exactly one item for every input ID."

    private fun message(role: String, content: String): JsonObject =
        buildJsonObject { put("role", JsonPrimitive(role)); put("content", JsonPrimitive(content)) }

    private fun isLoopback(host: String?): Boolean =
        host.equals("localhost", true) || host == "127.0.0.1" || host == "::1" || host == "[::1]"

    private fun safeError(body: String): String = body.replace(Regex("[\\u0000-\\u001F]"), " ").take(300)
}

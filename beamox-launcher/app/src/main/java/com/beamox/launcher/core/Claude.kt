package com.beamox.launcher.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(val role: String, val text: String)

object ClaudeModels {
    val all = listOf(
        "claude-opus-5-5" to "Claude Opus 5.5 — самый умный (по умолчанию)",
        "claude-sonnet-5-5" to "Claude Sonnet 5.5 — быстрее и дешевле",
        "claude-haiku-5-5" to "Claude Haiku 5.5 — самый быстрый",
    )
}

class ClaudeService(private val secrets: Secrets) {
    private var client: AnthropicClient? = null
    private var clientKey: String = ""

    private fun client(): AnthropicClient {
        val key = secrets.claudeKey
        if (key.isBlank()) throw IllegalStateException("Не задан ключ Claude. Откройте Настройки → Claude.")
        if (client == null || clientKey != key) {
            client = AnthropicOkHttpClient.builder().apiKey(key).build()
            clientKey = key
        }
        return client!!
    }

    /**
     * Один ход диалога. history — вся переписка (последнее сообщение — от пользователя).
     * context — живые данные офиса (статистика, задачи, почты), подставляются в system.
     */
    suspend fun ask(model: String, history: List<ChatMessage>, context: String, extra: String): String =
        withContext(Dispatchers.IO) {
            val today = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale("ru")).format(Date())
            val system = buildString {
                appendLine("Ты — Claude, встроенный в рабочий телефон компании Beamox (лаунчер «Beamox»).")
                appendLine(extra)
                appendLine("Сейчас: $today.")
                appendLine("Отвечай по-русски. На телефоне экран маленький: пиши коротко, без длинных таблиц.")
                appendLine("Если просят письмо — дай готовый текст письма с темой в первой строке «Тема: …».")
                appendLine()
                appendLine("Актуальные данные офиса Beamox:")
                appendLine(context)
            }
            val b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(system)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
            // Серверный запасной вариант при отказе модели (поддерживают Opus/Sonnet 5.5).
            if (model.startsWith("claude-opus") || model.startsWith("claude-sonnet")) {
                b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }
            history.takeLast(30).dropWhile { it.role != "user" }.forEach {
                if (it.role == "user") b.addUserMessage(it.text) else b.addAssistantMessage(it.text)
            }
            try {
                val msg = client().messages().create(b.build())
                if (msg.stopReason().orElse(null) == StopReason.REFUSAL) {
                    return@withContext "Claude отказался отвечать на этот запрос. Попробуйте переформулировать."
                }
                msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("").trim()
                    .ifBlank { "(пустой ответ)" }
            } catch (e: UnauthorizedException) {
                "Неверный ключ Claude. Проверьте его в Настройках."
            } catch (e: RateLimitException) {
                "Слишком много запросов к Claude. Подождите минуту."
            } catch (e: AnthropicServiceException) {
                "Ошибка Claude (${e.statusCode()}): ${e.message}"
            } catch (e: IllegalStateException) {
                e.message ?: "Ошибка"
            } catch (e: Exception) {
                "Нет связи с Claude: ${e.message}"
            }
        }

    companion object {
        fun loadHistory(prefs: Prefs): List<ChatMessage> = try {
            val a = JSONArray(prefs.chatJson)
            (0 until a.length()).map { a.getJSONObject(it).let { o -> ChatMessage(o.getString("r"), o.getString("t")) } }
        } catch (e: Exception) { emptyList() }

        fun saveHistory(prefs: Prefs, list: List<ChatMessage>) {
            val a = JSONArray()
            list.takeLast(60).forEach { a.put(JSONObject().put("r", it.role).put("t", it.text)) }
            prefs.chatJson = a.toString()
        }
    }
}

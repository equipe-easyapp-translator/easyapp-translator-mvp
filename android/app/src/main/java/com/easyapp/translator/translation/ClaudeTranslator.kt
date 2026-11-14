package com.easyapp.translator.translation

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.OverloadedException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.ThinkingConfigDisabled
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.easyapp.translator.R
import com.easyapp.translator.str

/** Resultado de uma chamada ao Claude: sucesso ou um motivo de falha já em português para o usuário. */
sealed interface ClaudeResult<out T> {
    data class Ok<T>(val value: T) : ClaudeResult<T>
    /** [permanent]: não adianta tentar de novo nesta sessão (chave inválida, sem crédito, limite de gasto). */
    data class Failed(val reason: String, val permanent: Boolean = false) : ClaudeResult<Nothing>
}

/** Consumo da sessão (FR-024). Só em memória; zerado ao iniciar e ao encerrar a sessão. */
data class ClaudeUsage(
    val requests: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val lastError: String? = null,
) {
    val estimatedCostUsd: Double
        get() = inputTokens * INPUT_USD_PER_TOKEN + outputTokens * OUTPUT_USD_PER_TOKEN

    companion object {
        // Claude Haiku 5.5, prompts de até 100 mil tokens: US$ 0,10 / US$ 0,50 por milhão (research.md, T042).
        const val INPUT_USD_PER_TOKEN = 0.10 / 1_000_000
        const val OUTPUT_USD_PER_TOKEN = 0.50 / 1_000_000
    }
}

object ClaudeUsageHolder {
    private val mutable = MutableStateFlow(ClaudeUsage())
    val usage: StateFlow<ClaudeUsage> = mutable.asStateFlow()

    fun reset() {
        mutable.value = ClaudeUsage()
    }

    internal fun record(message: Message) {
        val usage = message.usage()
        val current = mutable.value
        mutable.value = current.copy(
            requests = current.requests + 1,
            inputTokens = current.inputTokens + usage.inputTokens(),
            outputTokens = current.outputTokens + usage.outputTokens(),
            lastError = null,
        )
    }

    internal fun recordError(reason: String) {
        mutable.value = mutable.value.copy(lastError = reason)
    }
}

/**
 * Tradução e explicação com o Claude Haiku 5.5, configurado para gastar o mínimo
 * (NFR-006): raciocínio desligado, esforço baixo e teto de tokens por resposta.
 * Só texto reconhecido é enviado: nunca imagem nem áudio (FR-021).
 *
 * As chamadas são bloqueantes; rode fora da thread principal e da thread de captura.
 */
class ClaudeTranslator(apiKey: String) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .timeout(Duration.ofSeconds(20))
        .maxRetries(1)
        .build()

    /**
     * Traduz as linhas de uma mesma tela de uma vez, para o modelo usar as vizinhas
     * como contexto. A lista devolvida tem o mesmo tamanho da entrada; `null` numa
     * posição significa que essa linha não voltou e deve cair para o ML Kit.
     */
    fun translateLines(lines: List<String>, sourceLanguage: String, targetLanguage: String): ClaudeResult<List<String?>> {
        if (lines.isEmpty()) return ClaudeResult.Ok(emptyList())
        val source = languageName(sourceLanguage)
        val target = languageName(targetLanguage)
        val system = "You translate on-screen text captured by OCR from $source to $target. " +
            "The numbered lines come from the same screen; OCR may split one sentence across lines, so use neighbouring lines as context. " +
            "Translate idioms and slang by meaning, not word by word. Keep names, numbers and URLs unchanged. " +
            "Reply with exactly one line per input line, formatted as `N. translation`, using the same numbers, and nothing else."
        val input = lines.mapIndexed { index, line -> "${index + 1}. ${line.replace('\n', ' ')}" }.joinToString("\n")
        val maxTokens = (40L + lines.sumOf { it.length } / 2L + lines.size * 12L).coerceIn(64L, 1200L)
        return call(system, input, maxTokens) { text ->
            val byNumber = text.lineSequence()
                .mapNotNull { NUMBERED_LINE.matchEntire(it.trim()) }
                .associate { it.groupValues[1].toInt() to it.groupValues[2].trim() }
            List(lines.size) { index -> byNumber[index + 1]?.takeIf(String::isNotBlank) }
        }
    }

    /** "O que isso quer dizer?" (FR-022): explicação curta no idioma de destino. */
    fun explain(original: String, translated: String, sourceLanguage: String, targetLanguage: String): ClaudeResult<String> {
        val target = languageName(targetLanguage)
        val system = "You help a traveller understand a phrase seen on a phone screen. " +
            "Answer in $target, in at most three short sentences and with no preamble: what the original means, " +
            "its tone (formal, informal, slang) and any idiom or cultural note worth knowing."
        val input = "Original (${languageName(sourceLanguage)}): $original\nCurrent translation: $translated"
        return call(system, input, maxTokens = 220L) { it.trim() }
    }

    fun close() {
        runCatching { client.close() }
    }

    private fun <T> call(system: String, userText: String, maxTokens: Long, parse: (String) -> T): ClaudeResult<T> {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(maxTokens)
            .system(system)
            // Raciocínio estendido é cobrado como saída e não ajuda a traduzir uma linha.
            .thinking(ThinkingConfigDisabled.builder().build())
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .addUserMessage(userText)
            .build()
        return try {
            val message = client.messages().create(params)
            ClaudeUsageHolder.record(message)
            if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
                return failed(str(R.string.claude_err_refused))
            }
            val text = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("\n")
            if (text.isBlank()) failed(str(R.string.claude_err_empty)) else ClaudeResult.Ok(parse(text))
        } catch (_: UnauthorizedException) {
            failed(str(R.string.claude_err_key), permanent = true)
        } catch (_: PermissionDeniedException) {
            failed(str(R.string.claude_err_permission), permanent = true)
        } catch (_: RateLimitException) {
            failed(str(R.string.claude_err_rate))
        } catch (_: OverloadedException) {
            failed(str(R.string.claude_err_overloaded))
        } catch (exception: BadRequestException) {
            val detail = exception.message.orEmpty()
            when {
                detail.contains("credit", ignoreCase = true) -> failed(str(R.string.claude_err_credit), permanent = true)
                detail.contains("limit", ignoreCase = true) -> failed(str(R.string.claude_err_spend), permanent = true)
                else -> failed(str(R.string.claude_err_bad_request))
            }
        } catch (_: AnthropicServiceException) {
            failed(str(R.string.claude_err_service))
        } catch (_: AnthropicIoException) {
            failed(str(R.string.claude_err_network))
        } catch (_: RuntimeException) {
            failed(str(R.string.claude_err_unexpected))
        }
    }

    private fun failed(reason: String, permanent: Boolean = false): ClaudeResult.Failed {
        ClaudeUsageHolder.recordError(reason)
        return ClaudeResult.Failed(reason, permanent)
    }

    companion object {
        const val MODEL = "claude-haiku-5-5"
        private val NUMBERED_LINE = Regex("""^(\d{1,3})[.):]\s*(.*)$""")

        private fun languageName(code: String) = when (code) {
            "en" -> "English"
            "es" -> "Spanish"
            "it" -> "Italian"
            "fr" -> "French"
            "pt" -> "Brazilian Portuguese"
            else -> code
        }
    }
}

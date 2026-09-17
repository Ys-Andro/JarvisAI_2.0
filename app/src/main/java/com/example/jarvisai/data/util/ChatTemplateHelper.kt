package com.example.jarvisai.data.util

import com.example.jarvisai.domain.model.Message
import com.example.jarvisai.domain.model.Role

enum class ChatTemplateFormat {
    LLAMA_3,     // <|begin_of_text|><|start_header_id|>system...
    CHATML,      // <|im_start|>system...<|im_end|> (Qwen, Mistral, Yi)
    GEMMA,       // <start_of_turn>user...<end_of_turn>
    GENERIC      // ### System:\n...### User:\n...
}

object ChatTemplateHelper {

    fun detectFormat(modelName: String, fileName: String): ChatTemplateFormat {
        val combined = "$modelName $fileName".lowercase()
        return when {
            combined.contains("llama-3") || combined.contains("llama3") || combined.contains("llama_3") ->
                ChatTemplateFormat.LLAMA_3
            combined.contains("gemma") ->
                ChatTemplateFormat.GEMMA
            combined.contains("qwen") || combined.contains("chatml") || combined.contains("yi") || combined.contains("deepseek") ->
                ChatTemplateFormat.CHATML
            else ->
                ChatTemplateFormat.CHATML // Modern GGUF standard default
        }
    }

    fun formatPrompt(
        systemPrompt: String,
        history: List<Message>,
        newPrompt: String,
        format: ChatTemplateFormat
    ): String {
        return when (format) {
            ChatTemplateFormat.LLAMA_3 -> formatLlama3(systemPrompt, history, newPrompt)
            ChatTemplateFormat.CHATML -> formatChatML(systemPrompt, history, newPrompt)
            ChatTemplateFormat.GEMMA -> formatGemma(systemPrompt, history, newPrompt)
            ChatTemplateFormat.GENERIC -> formatGeneric(systemPrompt, history, newPrompt)
        }
    }

    private fun formatLlama3(systemPrompt: String, history: List<Message>, newPrompt: String): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|>")
        if (systemPrompt.isNotBlank()) {
            sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
            sb.append(systemPrompt.trim())
            sb.append("<|eot_id|>")
        }

        for (msg in history) {
            val headerRole = if (msg.role == Role.USER) "user" else "assistant"
            sb.append("<|start_header_id|>$headerRole<|end_header_id|>\n\n")
            sb.append(msg.content.trim())
            sb.append("<|eot_id|>")
        }

        sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
        sb.append(newPrompt.trim())
        sb.append("<|eot_id|>")
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    private fun formatChatML(systemPrompt: String, history: List<Message>, newPrompt: String): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("<|im_start|>system\n")
            sb.append(systemPrompt.trim())
            sb.append("<|im_end|>\n")
        }

        for (msg in history) {
            val roleStr = if (msg.role == Role.USER) "user" else "assistant"
            sb.append("<|im_start|>$roleStr\n")
            sb.append(msg.content.trim())
            sb.append("<|im_end|>\n")
        }

        sb.append("<|im_start|>user\n")
        sb.append(newPrompt.trim())
        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun formatGemma(systemPrompt: String, history: List<Message>, newPrompt: String): String {
        val sb = StringBuilder()
        // Gemma 2 typically bundles system instruction in the first user turn or supports system turn
        val effectiveHistory = history.toMutableList()

        if (effectiveHistory.isEmpty() && systemPrompt.isNotBlank()) {
            sb.append("<start_of_turn>user\n")
            sb.append(systemPrompt.trim()).append("\n\n").append(newPrompt.trim())
            sb.append("<end_of_turn>\n<start_of_turn>model\n")
            return sb.toString()
        }

        for (msg in history) {
            val roleStr = if (msg.role == Role.USER) "user" else "model"
            sb.append("<start_of_turn>$roleStr\n")
            sb.append(msg.content.trim())
            sb.append("<end_of_turn>\n")
        }

        sb.append("<start_of_turn>user\n")
        sb.append(newPrompt.trim())
        sb.append("<end_of_turn>\n<start_of_turn>model\n")
        return sb.toString()
    }

    private fun formatGeneric(systemPrompt: String, history: List<Message>, newPrompt: String): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("### System:\n").append(systemPrompt.trim()).append("\n\n")
        }

        for (msg in history) {
            val label = if (msg.role == Role.USER) "### User" else "### Assistant"
            sb.append("$label:\n").append(msg.content.trim()).append("\n\n")
        }

        sb.append("### User:\n").append(newPrompt.trim()).append("\n\n")
        sb.append("### Assistant:\n")
        return sb.toString()
    }
}

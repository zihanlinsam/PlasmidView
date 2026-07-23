package com.plasmidview.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val language: String = "english"
) {
    private val valid: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank()

    private val langInstruction = if (language == "chinese") "请用中文回答。不要使用表格，用列表代替。" else "Please provide answer in English. Do NOT use tables, use lists instead."

    suspend fun ask(systemPrompt: String, userPrompt: String, fast: Boolean = false): String {
        if (!valid) return if (language == "chinese") "AI 未配置。请在设置中配置 API Key。" else "AI not configured. Set API key in Settings."
        val sysMsg = "$langInstruction\n\n$systemPrompt"
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${baseUrl.trimEnd('/')}/chat/completions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("api-key", apiKey)
                conn.doOutput = true
                conn.connectTimeout = if (fast) 15000 else 30000
                conn.readTimeout = if (fast) 20000 else 60000

                val body = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role", "system"); put("content", sysMsg) })
                        put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
                    })
                    put("temperature", 0.3)
                }

                conn.outputStream.write(body.toString().toByteArray())
                val reader = BufferedReader(InputStreamReader(
                    if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream))
                val response = reader.readText()
                reader.close()

                if (conn.responseCode !in 200..299) {
                    return@withContext if (language == "chinese") "API 错误 (${conn.responseCode})" else "API error (${conn.responseCode})"
                }

                val json = JSONObject(response)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val msg = choices.getJSONObject(0).optJSONObject("message")
                    val content = msg?.optString("content", "")?.takeUnless { it.isBlank() }
                    content ?: if (language == "chinese") "无回复" else "No response"
                } else {
                    if (language == "chinese") "意外的 API 响应格式" else "Unexpected API response format"
                }
            } catch (e: Exception) {
                if (language == "chinese") "连接错误: ${e.message}" else "Connection error: ${e.message}"
            }
        }
    }
}

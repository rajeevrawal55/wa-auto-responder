package com.waautoresponder.gemini

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {

    data class Result(val success: Boolean, val text: String)

    fun generate(apiKey: String, model: String, systemPrompt: String, sender: String, message: String): Result {
        if (apiKey.isBlank()) return Result(false, "API key is missing.")

        val safeModel = if (model.isBlank()) "gemini-2.5-flash" else model.trim()
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + safeModel + ":generateContent")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        val combinedPrompt = systemPrompt.trim() +
            "\n\nCustomer name/contact: " + sender.ifBlank { "Unknown" } +
            "\nCustomer WhatsApp message: " + message +
            "\n\nWrite only the reply that should be sent to the customer. Do not add labels or commentary."

        val body = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", combinedPrompt)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.25)
                put("maxOutputTokens", 180)
            })
        }

        return try {
            connection.outputStream.use { out ->
                out.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = BufferedReader(stream.reader(Charsets.UTF_8)).use { it.readText() }

            if (code !in 200..299) {
                val messageText = try {
                    JSONObject(response).optJSONObject("error")?.optString("message").orEmpty()
                } catch (_: Exception) {
                    response
                }
                return Result(false, "Gemini error " + code + ": " + messageText.take(180))
            }

            val json = JSONObject(response)
            val candidates = json.optJSONArray("candidates")
            val text = candidates
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                .orEmpty()

            if (text.isBlank()) Result(false, "Gemini returned an empty reply.")
            else Result(true, text)
        } catch (e: Exception) {
            Result(false, "Connection failed: " + (e.message ?: e.javaClass.simpleName))
        } finally {
            connection.disconnect()
        }
    }
}

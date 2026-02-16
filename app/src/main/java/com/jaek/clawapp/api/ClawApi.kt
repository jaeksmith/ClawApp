package com.jaek.clawapp.api

import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Client for communicating with the OpenClaw gateway.
 * Uses the OpenResponses API for synchronous request/response.
 */
class ClawApi(
    private val baseUrl: String,
    private val token: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonType = "application/json".toMediaType()

    data class PingResponse(val ok: Boolean, val message: String? = null)

    /**
     * Simple connectivity check — hits the gateway health endpoint.
     */
    fun ping(callback: (Result<PingResponse>) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/health")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        callback(Result.success(PingResponse(ok = true)))
                    } else {
                        callback(Result.success(PingResponse(ok = false, message = "HTTP ${it.code}")))
                    }
                }
            }
        })
    }

    /**
     * Send a message to the agent via OpenResponses API and get a response.
     */
    fun sendMessage(message: String, callback: (Result<String>) -> Unit) {
        val body = gson.toJson(mapOf(
            "model" to "default",
            "input" to message
        )).toRequestBody(jsonType)

        val request = Request.Builder()
            .url("$baseUrl/v1/responses")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val responseBody = it.body?.string() ?: ""
                        try {
                            // Extract text from OpenResponses format
                            val parsed = gson.fromJson(responseBody, Map::class.java)
                            val output = parsed["output"] as? List<*>
                            val text = output?.filterIsInstance<Map<*, *>>()
                                ?.firstOrNull { item -> item["type"] == "message" }
                                ?.let { msg ->
                                    (msg["content"] as? List<*>)
                                        ?.filterIsInstance<Map<*, *>>()
                                        ?.firstOrNull { c -> c["type"] == "output_text" }
                                        ?.get("text") as? String
                                }
                            callback(Result.success(text ?: responseBody))
                        } catch (e: Exception) {
                            callback(Result.success(responseBody))
                        }
                    } else {
                        callback(Result.failure(IOException("HTTP ${it.code}: ${it.body?.string()}")))
                    }
                }
            }
        })
    }
}

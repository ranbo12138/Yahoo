package com.yahoo.translator

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class ChatRequest(val model: String, val messages: List<Message>, val temperature: Double = 0.3)
data class Message(val role: String, val content: String)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: Message)

interface OpenAIApi {
    @POST("chat/completions")
    suspend fun translate(@Body request: ChatRequest): ChatResponse
}

object ApiClient {
    private var api: OpenAIApi? = null
    private var url = ""
    private var key = ""
    
    fun init(baseUrl: String, apiKey: String) {
        if (baseUrl == url && apiKey == key && api != null) return
        url = baseUrl; key = apiKey
        
        val client = OkHttpClient.Builder()
            .addInterceptor { it.proceed(it.request().newBuilder().addHeader("Authorization", "Bearer $apiKey").build()) }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        api = Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(OpenAIApi::class.java)
    }
    
    fun get(): OpenAIApi = api ?: throw IllegalStateException("请先配置API")
}

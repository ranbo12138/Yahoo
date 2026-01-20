package com.yahoo.translator

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.3
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

interface OpenAIApi {
    @POST("chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun translate(@Body request: ChatRequest): ChatResponse
}

object ApiClient {
    private var api: OpenAIApi? = null
    private var currentBaseUrl = ""
    private var currentApiKey = ""
    
    fun initialize(baseUrl: String, apiKey: String) {
        if (baseUrl == currentBaseUrl && apiKey == currentApiKey && api != null) {
            return
        }
        
        currentBaseUrl = baseUrl
        currentApiKey = apiKey
        
        val logging = HttpLoggingInterceptor { message ->
            Logger.log("HTTP: $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAIApi::class.java)
    }
    
    fun getApi(): OpenAIApi = api ?: throw IllegalStateException("请先在设置中配置 API")
}

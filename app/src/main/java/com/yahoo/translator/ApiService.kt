package com.yahoo.translator

import com.google.gson.Gson
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class ChatRequest(val model: String, val messages: List<Message>, val temperature: Double = 0.3)
data class Message(val role: String, val content: String)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: Message)
data class ModelsResponse(val data: List<ModelInfo>)
data class ModelInfo(val id: String)

interface OpenAIApi {
    @POST("chat/completions")
    suspend fun translate(@Body request: ChatRequest): ChatResponse
    
    @GET("models")
    suspend fun getModels(): ModelsResponse
}

object ApiClient {
    private var api: OpenAIApi? = null
    private var url = ""
    private var key = ""
    private val gson = Gson()
    
    fun init(baseUrl: String, apiKey: String) {
        if (baseUrl == url && apiKey == key && api != null) return
        url = baseUrl; key = apiKey
        
        val loggingInterceptor = Interceptor { chain ->
            val request = chain.request()
            val startTime = System.currentTimeMillis()
            
            // 记录请求
            val reqHeaders = request.headers.toString()
            val reqBody = request.body?.let {
                val buffer = okio.Buffer()
                it.writeTo(buffer)
                buffer.readUtf8()
            } ?: ""
            
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            
            // 记录响应
            val resHeaders = response.headers.toString()
            val resBody = response.peekBody(Long.MAX_VALUE).string()
            
            AILogger.log(
                method = request.method,
                url = request.url.toString(),
                status = response.code,
                duration = duration,
                reqHeaders = reqHeaders,
                reqBody = reqBody,
                resHeaders = resHeaders,
                resBody = resBody
            )
            
            response
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor { it.proceed(it.request().newBuilder().addHeader("Authorization", "Bearer $apiKey").build()) }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        api = Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(OpenAIApi::class.java)
    }
    
    fun get(): OpenAIApi = api ?: throw IllegalStateException("请先配置API")
    
    suspend fun testConnection(): Result<List<String>> {
        return try {
            val models = get().getModels()
            Result.success(models.data.map { it.id })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

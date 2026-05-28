package com.example.router_app.data.ai

import com.example.router_app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiAddressExtractor(
    private val api: AiExtractionApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.AI_SERVER_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AiExtractionApiService::class.java),
) {
    suspend fun extract(rawOcrText: String, city: String): AiExtractionResult {
        return try {
            val response = api.extractAddress(
                apiKey = BuildConfig.AI_API_KEY,
                request = ExtractRequest(ocr_text = rawOcrText, city = city),
            )
            if (response.success && response.address.isNotBlank()) {
                AiExtractionResult.Success(response.address)
            } else {
                AiExtractionResult.Error(AiExtractionResult.ErrorType.AddressNotFound)
            }
        } catch (e: IOException) {
            AiExtractionResult.Error(AiExtractionResult.ErrorType.ConnectionError)
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> AiExtractionResult.Error(AiExtractionResult.ErrorType.AuthError)
                504 -> AiExtractionResult.Error(AiExtractionResult.ErrorType.Timeout)
                else -> AiExtractionResult.Error(AiExtractionResult.ErrorType.ConnectionError)
            }
        }
    }
}

package com.example.ekh2026.data.network

import com.example.ekh2026.data.model.CriterionDto
import com.example.ekh2026.data.model.JurorLoginRequest
import com.example.ekh2026.data.model.JurorLoginResponse
import com.example.ekh2026.data.model.JurorLoginStatusResponse
import com.example.ekh2026.data.model.ParticipantDto
import com.example.ekh2026.data.model.RefreshRequest
import com.example.ekh2026.data.model.RefreshResponse
import com.example.ekh2026.data.model.ScoreDto
import com.example.ekh2026.data.model.UpsertMyScoreRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import java.util.concurrent.TimeUnit

interface AuthApi {
    @GET("public/juror-login-status")
    suspend fun jurorLoginStatus(): Response<JurorLoginStatusResponse>

    @POST("auth/juror/login")
    suspend fun jurorLogin(@Body body: JurorLoginRequest): Response<JurorLoginResponse>

    @POST("auth/refresh")
    suspend fun refreshAccessToken(
        @Header("Authorization") authorization: String,
        @Body body: RefreshRequest
    ): Response<RefreshResponse>
}

interface JurorApi {
    @GET("juror/participants")
    suspend fun getParticipants(
        @Header("Authorization") authorization: String
    ): Response<List<ParticipantDto>>

    @GET("juror/criteria")
    suspend fun getCriteria(
        @Header("Authorization") authorization: String
    ): Response<List<CriterionDto>>

    @GET("juror/scores/me")
    suspend fun getMyScores(
        @Header("Authorization") authorization: String
    ): Response<List<ScoreDto>>

    @PUT("juror/scores/me")
    suspend fun upsertMyScore(
        @Header("Authorization") authorization: String,
        @Body body: UpsertMyScoreRequest
    ): Response<ScoreDto>
}

data class JurorNetworkClients(
    val authApi: AuthApi,
    val jurorApi: JurorApi,
    val httpClient: OkHttpClient
)

object JurorApiServiceFactory {
    fun create(baseUrl: String): JurorNetworkClients {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(httpClient)
            .build()

        return JurorNetworkClients(
            authApi = retrofit.create(AuthApi::class.java),
            jurorApi = retrofit.create(JurorApi::class.java),
            httpClient = httpClient
        )
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
    }
}

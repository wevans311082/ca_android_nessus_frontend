package com.wevans.caandroidnessusfrontend.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

internal class NessusAuthInterceptor(
    private val authProvider: () -> NessusAuth
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val auth = authProvider()
        val request = chain.request().newBuilder().apply {
            if (auth.accessKey.isNotBlank() && auth.secretKey.isNotBlank()) {
                header("X-ApiKeys", "accessKey=${auth.accessKey}; secretKey=${auth.secretKey}")
            }
            header("Accept", "application/json")
        }.build()
        return chain.proceed(request)
    }
}

data class NessusAuth(
    val accessKey: String = "",
    val secretKey: String = ""
)

interface NessusApiService {
    @GET("scans")
    suspend fun listScans(): NessusScansResponse

    @GET("scans/{id}")
    suspend fun getScan(@Path("id") id: Int): NessusScanDetailResponse

    @GET("plugins/plugin/{pluginId}")
    suspend fun getPlugin(@Path("pluginId") pluginId: Int): NessusPluginResponse

    @POST("scans/{id}/launch")
    suspend fun startScan(@Path("id") id: Int, @Body body: StartScanRequest = StartScanRequest())

    @POST("scans/{id}/stop")
    suspend fun stopScan(@Path("id") id: Int)

    @DELETE("scans/{id}")
    suspend fun deleteScan(@Path("id") id: Int)

    @PUT("scans/{id}")
    suspend fun updateScanSettings(@Path("id") id: Int, @Body body: UpdateScanSettingsRequest)

    @GET("groups")
    suspend fun listGroups(): NessusGroupsResponse

    @POST("groups")
    suspend fun createGroup(@Body body: CreateGroupRequest)

    @DELETE("groups/{groupId}")
    suspend fun deleteGroup(@Path("groupId") groupId: Int)

    @GET("scanners/{scannerId}/agents")
    suspend fun listAgents(@Path("scannerId") scannerId: Int = 1): NessusAgentsResponse

    @POST("scanners/{scannerId}/agents/{agentId}/unlink")
    suspend fun unlinkAgent(@Path("scannerId") scannerId: Int = 1, @Path("agentId") agentId: Int)
}

class NessusApiFactory(
    private val authProvider: () -> NessusAuth
) {
    fun create(baseUrl: String): NessusApiService {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(NessusAuthInterceptor(authProvider))
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(NessusApiService::class.java)
    }

    companion object {
        fun normalizeBaseUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().removeSuffix("/")
            return "$trimmed/"
        }
    }
}

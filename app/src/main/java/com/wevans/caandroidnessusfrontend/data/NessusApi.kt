package com.wevans.caandroidnessusfrontend.data

import com.wevans.caandroidnessusfrontend.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
import retrofit2.http.Query

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
    @GET("server/status")
    suspend fun getServerStatus(): ServerStatusResponse

    @GET("scans")
    suspend fun listScans(): NessusScansResponse

    @GET("scans/{id}")
    suspend fun getScan(@Path("id") id: Int, @Query("history_id") historyId: Int? = null): NessusScanDetailResponse

    @GET("scans/{scanId}/hosts/{hostId}")
    suspend fun getScanHost(@Path("scanId") scanId: Int, @Path("hostId") hostId: Int, @Query("history_id") historyId: Int? = null): NessusScanHostResponse

    @GET("plugins/plugin/{pluginId}")
    suspend fun getPlugin(@Path("pluginId") pluginId: Int): NessusPluginResponse

    @POST("scans/{id}/launch")
    suspend fun startScan(@Path("id") id: Int, @Body body: StartScanRequest = StartScanRequest())

    @POST("scans/{id}/stop")
    suspend fun stopScan(@Path("id") id: Int)

    @POST("scans/{id}/pause")
    suspend fun pauseScan(@Path("id") id: Int)

    @DELETE("scans/{id}")
    suspend fun deleteScan(@Path("id") id: Int)

    @PUT("scans/{id}")
    suspend fun updateScanSettings(@Path("id") id: Int, @Body body: UpdateScanSettingsRequest)

    @POST("scans/{scanId}/export")
    suspend fun exportScan(@Path("scanId") scanId: Int, @Body body: ExportScanRequest): ExportScanResponse
    
    @GET("scans/{scanId}/export/{fileId}/status")
    suspend fun getExportStatus(@Path("scanId") scanId: Int, @Path("fileId") fileId: String): ExportStatusResponse
    
    @GET("scans/{scanId}/export/{fileId}/download")
    suspend fun downloadScan(@Path("scanId") scanId: Int, @Path("fileId") fileId: String): okhttp3.ResponseBody


    @GET("groups")
    suspend fun listGroups(): NessusGroupsResponse

    @POST("groups")
    suspend fun createGroup(@Body body: CreateGroupRequest)

    @DELETE("groups/{groupId}")
    suspend fun deleteGroup(@Path("groupId") groupId: Int)

    @GET("scanners/{scannerId}/agent-groups")
    suspend fun listAgentGroups(@Path("scannerId") scannerId: Int = 1): NessusAgentGroupsResponse

    @GET("scanners/{scannerId}/agent-groups/{groupId}/agents")
    suspend fun listAgentsInGroup(@Path("groupId") groupId: Int, @Path("scannerId") scannerId: Int = 1): NessusAgentsResponse

    @GET("scanners/{scannerId}/agents")
    suspend fun listAgents(@Path("scannerId") scannerId: Int = 1): NessusAgentsResponse

    @POST("scanners/{scannerId}/agents/{agentId}/unlink")
    suspend fun unlinkAgent(@Path("scannerId") scannerId: Int = 1, @Path("agentId") agentId: Int)
}

class NessusApiFactory(
    private val authProvider: () -> NessusAuth
) {
    fun create(baseUrl: String): NessusApiService {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val client = OkHttpClient.Builder()
            .addInterceptor(NessusAuthInterceptor(authProvider))
            .apply {
                if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                    addInterceptor(logging)
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
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

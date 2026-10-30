package com.chengying.clearview.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface SegmentApiService {
    @GET
    suspend fun downloadByRelativeUrl(
        @Url relativeUrl: String,
        @Header("X-Install-Id") installId: String,
    ): ResponseBody

    @Multipart
    @POST("api/v1/analyze")
    suspend fun analyze(
        @Part image: MultipartBody.Part,
        @Part("top_k") topK: RequestBody?,
        @Part("tta_mode") ttaMode: RequestBody?,
        @Part("segmentMode") segmentMode: RequestBody?,
        @Part("clientRecordId") clientRecordId: RequestBody,
        @Header("X-Install-Id") installId: String,
        @Part("projection") projection: RequestBody,
        @Part("acquiredAt") acquiredAt: RequestBody?,
        @Part("completenessConfirmed") completenessConfirmed: RequestBody,
    ): AnalyzeResponseDto

    @DELETE("api/v1/records/{recordId}")
    suspend fun deleteRecord(
        @Path("recordId") recordId: String,
        @Header("X-Install-Id") installId: String,
    )

    @GET("api/v1/records")
    suspend fun listRecords(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Header("X-Install-Id") installId: String,
    ): AnalyzeRecordsResponseDto
}

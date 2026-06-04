package com.qingmo.app.data.auth

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApiService {
    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Header("Authorization") token: String): AuthResponse

    @GET("api/v1/auth/me")
    suspend fun getMe(@Header("Authorization") token: String): UserInfo

    @PUT("api/v1/auth/me")
    suspend fun updateMe(
        @Header("Authorization") token: String,
        @Body body: ProfileUpdateRequest,
    ): Map<String, Any>
}

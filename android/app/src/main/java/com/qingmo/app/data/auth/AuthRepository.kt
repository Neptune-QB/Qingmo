package com.qingmo.app.data.auth

import com.qingmo.app.data.api.RetrofitClient
import kotlinx.coroutines.CancellationException

private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
    try { Result.success(block()) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }

class AuthRepository {
    private val api = RetrofitClient
        .okHttpClient
        .let { client ->
            val gson = com.google.gson.GsonBuilder()
                .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
            retrofit2.Retrofit.Builder()
                .baseUrl(RetrofitClient.BASE_URL)
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(gson))
                .build()
                .create(AuthApiService::class.java)
        }

    suspend fun register(req: RegisterRequest): Result<AuthResponse> =
        safeCall { api.register(req) }

    suspend fun login(req: LoginRequest): Result<AuthResponse> =
        safeCall { api.login(req) }

    suspend fun getMe(token: String): Result<UserInfo> =
        safeCall { api.getMe("Bearer $token") }
}

package com.jks.jatrav3.api

import retrofit2.Response

class JatraRepository(private val api: JatraApi = ApiClient.jatraApi) {
    suspend fun register(req: RegisterRequest): Response<RegisterResponse> {
        return api.register(req)
    }
    suspend fun loginWithOtp(req: LoginOtpRequest): Response<LoginOtpResponse> {
        return api.loginOtp(req)
    }
    suspend fun fetchArFiles(customerId: String) = api.getArFiles(customerId)

}
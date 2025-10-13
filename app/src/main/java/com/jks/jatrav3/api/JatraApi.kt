package com.jks.jatrav3.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Headers
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query


interface JatraApi {
    @Headers("Content-Type: application/json")
    @POST("jatra/api/reg")
    suspend fun register(
        @Body body: RegisterRequest
    ): Response<RegisterResponse>

    @Headers("Content-Type: application/json")
    @POST("jatra/api/login/otp")
    suspend fun loginOtp(@Body body: LoginOtpRequest): Response<LoginOtpResponse>

    @Multipart
    @POST("loan/api/create/loan")
    suspend fun createLoanMultipart(
        @Part("customer_id") customer: RequestBody,
        @Part("local_body_type") localBodyType: RequestBody?,
        @Part("loan_amount") loanAmount: RequestBody?,
        @Part("property_size_length") propertySizeLength: RequestBody,
        @Part("property_size_breadth") propertySizeBreadth: RequestBody,
        @Part("is_approach") isApproach: RequestBody?,
        @Part("approach_direction") approachDirection: RequestBody?,
        @Part("house_facing_direction") houseFacingDirection: RequestBody?,

        @Part gift_deed: MultipartBody.Part?,
        @Part jamabandi: MultipartBody.Part?,
        @Part trace_map: MultipartBody.Part?,
        @Part property_image: List<MultipartBody.Part>?,
        @Part("payment_id") payment_id: RequestBody?,
        @Part("payment_status") payment_status: RequestBody?
    ): Response<CreateLoanResponse>

    @GET("jatra/api/design/visualisation")
    suspend fun getVisualizations(): Response<DesignResponse>

    @GET("loan/api/loan/status")
    suspend fun getLoanStatus(@Query("c_id") cId: String): Response<LoanStatusResponse>

    @GET("jatra/api/get/quotation")
    suspend fun getQuotation(@Query("c_id") cId: String): Response<QuotationStatusResponse>

    @Multipart
    @POST("ar/api/super/ar/file/upload")
    suspend fun uploadArFile(
        @Part("c_id") cId: RequestBody,
        @Part arFile: MultipartBody.Part,
        @Part bluePrintFile: MultipartBody.Part,
        @Part("p_status") pStatus: RequestBody,
        @Part("p_id") pId: RequestBody
    ): Response<UploadResponse>

    @GET("jatra/api/ar/prepaid/amount")
    suspend fun getPrepaidAmount(): Response<PrepaidResponse>

    @GET("ar/api/super/ar/files/{customer_id}")
    suspend fun getArFiles(@Path("customer_id") customerId: String): SuperArResponse

    @GET("jatra/api/about")
    suspend fun getAbout(): Response<AboutResponse>

    @GET("jatra/api/data/privacy")
    suspend fun getPrivacy(): Response<PrivacyResponse>

    @GET("jatra/api/prepaid/amount")
    suspend fun getLoanPrepaidAmount(): Response<LoanPrepaidResponse>

    @Multipart
    @POST("jatra/api/upload/user/image")
    suspend fun uploadUserImage(
        @Part("u_id") uId: RequestBody,
        @Part user_image: MultipartBody.Part
    ): Response<UploadProfileResponse>

}
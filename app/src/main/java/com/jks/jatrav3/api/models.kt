package com.jks.jatrav3.api

import com.google.gson.annotations.SerializedName


data class JatraCustomer(
    val f_name: String?,
    val s_name: String?,
    val address: String?,
    val ad_line: String?,
    val pincode: String?,
    val city: String?,
    val state: String?,
    val contact: String?,
    val email: String?,
    val pwd: String?,      // hashed password returned
    val _id: String?,
    val __v: Int?
)

data class RegisterResponse(
    val message: String?,
    val jatra_customer: JatraCustomer?
)
data class LoginOtpRequest(
    val c_number: String,
    val otp: String
)

// Response from OTP login
data class LoginOtpResponse(
    val message: String?,
    val otp: String?,
    val jatra_customer: JatraCustomer?
)
data class PropertySize(
    val length: String?,
    val breadth: String?
)

data class Documents(
    val giftDeed: String?,        // filename or URL if server expects string
    val jamabandi: String?,
    val traceMap: String?,
    val propertyImages: List<String>?
)

// request model (JSON)
data class CreateLoanRequest(
    val customer: String,                 // customer id
    val local_body_type: String?,
    val loan_amount: String?,
    val property_size: PropertySize?,
    val is_approach: String?,
    val approach_direction: String?,
    val house_facing_direction: String?,
    val documents: Documents?,
    val payment_id: String?,
    val payment_status: String?
// if server expects filenames/urls
)

// response models (based on your sample)
data class CreatedLoan(
    val customer: String?,
    val local_body_type: String?,
    val loan_amount: String?,
    val property_size: PropertySize?,
    val is_approach: String?,
    val approach_direction: String?,
    val house_facing_direction: String?,
    val documents: Documents?,
    val loan_status: String?,
    val _id: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val __v: Int?,
    val payment_id: String?,
    val payment_status: String?
)

data class CreateLoanResponse(
    val success: Boolean?,
    val created_loan: CreatedLoan?
)

data class DesignResponse(
    val message: String?,
    val all_designs: List<DesignItem>?
)

data class DesignItem(
    val _id: String?,
    val link: String?,
    val image: String?,
    val description: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val __v: Int?
)

data class LoanStatusResponse(
    val success: Boolean,
    @SerializedName("loan_info") val loanInfo: LoanInfo?
)
data class QuotationStatusResponse(
    val message: String?,

    )

data class LoanInfo(
    @SerializedName("property_size") val propertySize: PropertySize?,
    val documents: Documents?,
    @SerializedName("_id") val id: String?,
    val customer: String?,
    @SerializedName("local_body_type") val localBodyType: String?,
    @SerializedName("loan_amount") val loanAmount: String?,
    @SerializedName("is_approach") val isApproach: String?,
    @SerializedName("approach_direction") val approachDirection: String?,
    @SerializedName("house_facing_direction") val houseFacingDirection: String?,
    @SerializedName("loan_status") val loanStatus: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

data class UploadResponse(
    val message: String?,
    @SerializedName("superAR") val superAR: SuperAR?
)

data class SuperAR(
    val customer: String?,
    val request_type: String?,
    val payment_status: String?,
    val payment_id: String?,
    val file_link: String?,
    val _id: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val __v: Int?
)
data class PrepaidResponse(
    @SerializedName("ar_prepaid_info")
    val arPrepaidInfo: List<PrepaidInfo>?
)

data class PrepaidInfo(
    val _id: String?,
    @SerializedName("prepaid_value")
    val prepaidValue: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val __v: Int?
)

data class SuperArResponse(
    val message: String,
    val super_ar_user: List<SuperArUser>
)

data class SuperArUser(
    val _id: String,
    val customer: String,
    val request_type: String?,
    val payment_status: String?,
    val payment_id: String?,
    val file_link: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val __v: Int?
)
data class AboutResponse(
    val message: String,
    val about_jatra: List<AboutItem>
)

data class AboutItem(
    val _id: String?,
    val details: String?,
    val __v: Int?
)
data class LoanPrepaidResponse(
    val loan_prepaid_info: List<LoanPrepaidInfo>?
)

data class LoanPrepaidInfo(
    val _id: String?,
    val prepaid_value: String?, // API returns string like "100"
    val createdAt: String?,
    val updatedAt: String?
)

data class PrivacyResponse(
    val message: String,
    val privacy_jatra: List<PrivacyItem>
)

data class PrivacyItem(
    val _id: String?,
    val details: String?,
    val __v: Int?
)

data class UploadProfileResponse(
    val message: String,
    val image_path: String,
    val updated_user: UpdatedUser?
)

data class UpdatedUser(
    val _id: String,
    val thumbnail: String?,
    val f_name: String?,
    val s_name: String?,
    val address: String?,
    val ad_line: String?,
    val pincode: String?,
    val city: String?,
    val state: String?,
    val contact: String?,
    val email: String?,
    val pwd: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val __v: Int?
)



package com.jks.jatrav3.api

data class RegisterRequest(
    val f_name: String,
    val s_name: String,
    val address: String,
    val ad_line: String,
    val pincode: String,
    val city: String,
    val state: String,
    val contact: String,
    val email: String,
    val pwd: String
)
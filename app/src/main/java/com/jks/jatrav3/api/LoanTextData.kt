package com.jks.jatrav3.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LoanTextData(
    val localBodyType: String,
    val isApproach: String,                // "yes" or "no"
    val houseFacingDirection: String,      // "North", "South", "East", "West", or "Other"
    val propertyLength: String,
    val propertyBreadth: String,
    val approachDirection: String,   // NEW
    val customerId: String,
    val loanAmount: String           // NEW
) : Parcelable

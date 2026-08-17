package com.example.resilio.model

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val address: String = "",
    val birthday: String = "",
    val idNumber: String = "",
    val role: UserRole = UserRole.RESIDENT,
    val verificationStatus: VerificationStatus = VerificationStatus.NOT_SUBMITTED,
    val idImageUrl: String? = null,
    val fcmToken: String? = null
)

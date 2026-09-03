package com.example.resilio.model

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val address: String = "",
    val birthday: String = "",
    val role: UserRole = UserRole.RESIDENT,
    val verificationStatus: VerificationStatus = VerificationStatus.NOT_SUBMITTED,
    val idImageUrl: String? = null,
    val idBackImageUrl: String? = null,
    val sex: String = "",
    val fcmToken: String? = null,
    // Profile Management Fields
    val position: String = "",
    val barangayName: String = "",
    val contactNumber: String = "",
    val about: String = "",
    val profileImageUrl: String? = null,
    val rejectionReason: String? = null
)

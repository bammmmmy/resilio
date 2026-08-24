package com.example.resilio.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class EmergencyAlert(
    val id: String = "",
    val title: String = "",
    @get:PropertyName("content") @set:PropertyName("content") var content: String = "",
    val type: HazardType = HazardType.GENERAL_ALERT,
    val authorUid: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Timestamp? = null,
    val affectedAreas: String = "",
    val evacuationCenter: String = ""
) {
    @get:PropertyName("message") @set:PropertyName("message") var message: String? = null
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Timestamp? = null

    val safeContent: String
        get() = content.ifEmpty { message ?: "" }

    val safeTimestamp: Timestamp
        get() = timestamp ?: createdAt ?: Timestamp.now()
}

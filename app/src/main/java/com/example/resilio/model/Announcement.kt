package com.example.resilio.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

enum class AnnouncementStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class HazardType {
    FLOOD,
    TYPHOON,
    EARTHQUAKE,
    LANDSLIDE,
    GENERAL_ALERT
}

data class Announcement(
    val id: String = "",
    val title: String = "",
    @get:PropertyName("content") @set:PropertyName("content") var content: String = "",
    val type: HazardType = HazardType.GENERAL_ALERT,
    val status: AnnouncementStatus = AnnouncementStatus.PENDING,
    val authorUid: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Timestamp? = null,
    val affectedAreas: String = "",
    val evacuationCenter: String = ""
) {
    // Compatibility fields for old database entries
    @get:PropertyName("message") @set:PropertyName("message") var message: String? = null
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Timestamp? = null

    // Safe getters that resolve either field
    val safeContent: String
        get() = content.ifEmpty { message ?: "" }

    val safeTimestamp: Timestamp
        get() = timestamp ?: createdAt ?: Timestamp.now()
}

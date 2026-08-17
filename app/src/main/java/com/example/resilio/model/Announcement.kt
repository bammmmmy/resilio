package com.example.resilio.model

import com.google.firebase.Timestamp

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
    val content: String = "",
    val type: HazardType = HazardType.GENERAL_ALERT,
    val status: AnnouncementStatus = AnnouncementStatus.PENDING,
    val authorUid: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val affectedAreas: String = "",
    val evacuationCenter: String = ""
)

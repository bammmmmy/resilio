package com.example.resilio.model

import com.google.firebase.Timestamp

data class EmergencyAlert(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val type: HazardType = HazardType.GENERAL_ALERT,
    val authorUid: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val affectedAreas: String = "",
    val evacuationCenter: String = ""
)

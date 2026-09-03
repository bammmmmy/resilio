package com.example.resilio.model

import com.google.firebase.Timestamp

data class EmergencyReport(
    val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val type: String = "", // Medical, Fire, Flood, etc.
    val description: String = "",
    val imageUrl: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: ReportStatus = ReportStatus.PENDING,
    val timestamp: Timestamp = Timestamp.now(),
    val responderNotes: String? = null
)

enum class ReportStatus {
    PENDING,
    RESPONDING,
    RESOLVED
}

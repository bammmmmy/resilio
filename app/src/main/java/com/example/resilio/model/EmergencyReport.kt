package com.example.resilio.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

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
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    val responderNotes: String? = null
) {
    val safeTimestamp: Timestamp
        get() = timestamp ?: Timestamp.now()
}

enum class ReportStatus {
    PENDING,
    RESPONDING,
    RESOLVED
}

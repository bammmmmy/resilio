package com.example.resilio.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class ReportChatMessage(
    val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "", // user, admin
    val message: String = "",
    @ServerTimestamp
    val timestamp: Timestamp? = null
) {
    val safeTimestamp: Long
        get() = timestamp?.seconds?.times(1000) ?: System.currentTimeMillis()
}

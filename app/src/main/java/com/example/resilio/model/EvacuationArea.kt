package com.example.resilio.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class EvacuationArea(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdBy: String = ""
)

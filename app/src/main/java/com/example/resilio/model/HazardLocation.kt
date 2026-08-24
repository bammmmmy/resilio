package com.example.resilio.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class HazardLocation(
    val id: String = "",
    val hazardType: String = "",
    val description: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Double = 0.0, // Radius in meters
    val createdBy: String = "",
)

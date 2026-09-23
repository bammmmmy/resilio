package com.example.resilio

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

object ActivityLogWriter {
    fun write(action: String, entityType: String, entityId: String = "", entityName: String = "", details: String = "") {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(user.uid).get().addOnSuccessListener { snapshot ->
            val profile = snapshot.data ?: emptyMap<String, Any>()
            db.collection("activity_logs").add(
                mapOf(
                    "action" to action,
                    "entityType" to entityType,
                    "entityId" to entityId,
                    "entityName" to entityName,
                    "details" to details,
                    "actorUid" to user.uid,
                    "actorName" to (profile["fullName"] as? String ?: user.displayName ?: user.email ?: "Admin"),
                    "actorEmail" to (user.email ?: ""),
                    "timestamp" to Timestamp(Date())
                )
            )
        }
    }
}
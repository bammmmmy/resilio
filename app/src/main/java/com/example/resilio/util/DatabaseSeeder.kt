package com.example.resilio.util

import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import com.example.resilio.model.VerificationStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object DatabaseSeeder {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun seedDefaultAccounts(onComplete: () -> Unit) {
        val defaults = listOf(
            Triple("user@resilio.com", "user1234", UserRole.RESIDENT),
            Triple("userhead@resilio.com", "userhead1234", UserRole.BDRRMO),
            Triple("userchairman@resilio.com", "userchairman", UserRole.CHAIRMAN)
        )

        var count = 0
        defaults.forEach { (email, password, role) ->
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = User(
                        uid = result.user!!.uid,
                        fullName = when(role) {
                            UserRole.RESIDENT -> "Default Resident"
                            UserRole.BDRRMO -> "Head of BDRRMO"
                            UserRole.CHAIRMAN -> "Barangay Chairman"
                        },
                        address = "Brgy. San Jose, Antipolo City",
                        role = role,
                        verificationStatus = VerificationStatus.APPROVED
                    )
                    db.collection("users").document(user.uid).set(user)
                        .addOnCompleteListener {
                            count++
                            if (count == defaults.size) onComplete()
                        }
                }
                .addOnFailureListener {
                    count++
                    if (count == defaults.size) onComplete()
                }
        }
    }
}

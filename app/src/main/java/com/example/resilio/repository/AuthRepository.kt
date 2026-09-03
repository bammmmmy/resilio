package com.example.resilio.repository

import com.example.resilio.model.User
import com.example.resilio.notifications.PushNotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import com.google.firebase.storage.FirebaseStorage
import android.net.Uri

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    fun login(email: String, pass: String, onResult: (Result<User>) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                PushNotificationManager.subscribeToTopics()
                getUserData(result.user?.uid ?: "", onResult)
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun register(user: User, pass: String, onResult: (Result<User>) -> Unit) {
        auth.createUserWithEmailAndPassword(user.email, pass)
            .addOnSuccessListener { result ->
                val finalUser = user.copy(uid = result.user?.uid ?: "")
                saveUserData(finalUser, onResult)
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    private fun saveUserData(user: User, onResult: (Result<User>) -> Unit) {
        db.collection("users").document(user.uid).set(user)
            .addOnSuccessListener {
                PushNotificationManager.subscribeToTopics()
                onResult(Result.success(user))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun getUserData(uid: String, onResult: (Result<User>) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java)
                if (user != null) {
                    onResult(Result.success(user))
                } else {
                    onResult(Result.failure(Exception("User not found in database")))
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun updateProfile(user: User, onResult: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(Result.failure(Exception("Not logged in")))
        db.collection("users").document(uid).set(user)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun uploadProfileImage(uri: Uri, onResult: (Result<String>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(Result.failure(Exception("Not logged in")))
        val ref = storage.reference.child("profile_images/$uid.jpg")
        
        ref.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                ref.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                onResult(Result.success(downloadUri.toString()))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun logout() {
        PushNotificationManager.unregisterFromPush()
        auth.signOut()
    }

    fun getCurrentUserUid(): String? = auth.currentUser?.uid
}

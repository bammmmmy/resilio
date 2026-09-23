package com.example.resilio.repository

import android.net.Uri
import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import com.example.resilio.model.VerificationStatus
import com.example.resilio.notifications.PushNotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://resilio-ab61f.firebasestorage.app")

    fun login(email: String, pass: String, onResult: (Result<User>) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user
                if (firebaseUser == null) {
                    onResult(Result.failure(Exception("User not found.")))
                    return@addOnSuccessListener
                }

                getUserData(firebaseUser.uid) { userResult ->
                    userResult.onSuccess { user ->
                        val isAdmin = user.role == UserRole.BDRRMO || user.role == UserRole.CHAIRMAN

                        if (!isAdmin && !firebaseUser.isEmailVerified) {
                            auth.signOut()
                            onResult(Result.failure(Exception("Please verify your email before logging in.")))
                            return@getUserData
                        }

                        PushNotificationManager.subscribeToTopics()
                        onResult(Result.success(user))
                    }.onFailure {
                        auth.signOut()
                        onResult(Result.failure(Exception("User profile not found or not yet approved.")))
                    }
                }
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun register(user: User, pass: String, onResult: (Result<User>) -> Unit) {
        auth.createUserWithEmailAndPassword(user.email, pass)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user ?: run {
                    onResult(Result.failure(Exception("Unable to create account.")))
                    return@addOnSuccessListener
                }

                val finalUser = user.copy(
                    uid = firebaseUser.uid
                )

                firebaseUser.sendEmailVerification()
                    .addOnSuccessListener {
                        saveUserData(finalUser, onResult)
                    }
                    .addOnFailureListener { verificationError ->
                        onResult(Result.failure(Exception("Account created, but verification email could not be sent: ${verificationError.message}")))
                    }
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    private fun saveUserData(user: User, onResult: (Result<User>) -> Unit) {
        db.collection("users").document(user.uid).set(user)
            .addOnSuccessListener {
                onResult(Result.success(user))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    private fun normalizeResidentVerificationStatus(user: User, onResult: (Result<User>) -> Unit) {
        val hasSubmittedId = !user.idImageUrl.isNullOrBlank() &&
            !user.idBackImageUrl.isNullOrBlank()
        val isUnsubmittedResident = user.role == UserRole.RESIDENT &&
            user.verificationStatus == VerificationStatus.PENDING &&
            !hasSubmittedId

        if (!isUnsubmittedResident) {
            onResult(Result.success(user))
            return
        }

        val correctedUser = user.copy(verificationStatus = VerificationStatus.NOT_SUBMITTED)
        db.collection("users").document(user.uid)
            .update("verificationStatus", VerificationStatus.NOT_SUBMITTED)
            .addOnSuccessListener { onResult(Result.success(correctedUser)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun getUserData(uid: String, onResult: (Result<User>) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java)
                if (user != null) {
                    normalizeResidentVerificationStatus(user, onResult)
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

    fun resetPassword(email: String, onResult: (Result<Unit>) -> Unit) {
        if (email.isBlank()) {
            onResult(Result.failure(Exception("Please enter your email address.")))
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                onResult(Result.success(Unit))
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
    fun getCurrentFirebaseUser() = auth.currentUser
}

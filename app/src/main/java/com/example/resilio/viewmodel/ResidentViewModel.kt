package com.example.resilio.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ResidentViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _userProfile = MutableLiveData<User>()
    val userProfile: LiveData<User> = _userProfile

    private val _announcements = MutableLiveData<List<Announcement>>()
    val announcements: LiveData<List<Announcement>> = _announcements

    fun fetchProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener {
            it.toObject(User::class.java)?.let { user -> _userProfile.postValue(user) }
        }
    }

    fun listenToAnnouncements() {
        db.collection("announcements")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    android.util.Log.e("ResidentViewModel", "Listen failed.", error)
                    return@addSnapshotListener
                }

                val allAnnouncements = value?.toObjects(Announcement::class.java) ?: emptyList()
                
                // Filter and sort client-side to avoid index requirement
                val approvedSorted = allAnnouncements
                    .filter { it.status == AnnouncementStatus.APPROVED }
                    .sortedByDescending { it.safeTimestamp }
                
                _announcements.postValue(approvedSorted)
            }
    }
}

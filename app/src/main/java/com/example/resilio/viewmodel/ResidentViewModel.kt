package com.example.resilio.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.EmergencyAlert
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

    private val _archivedItems = MutableLiveData<List<Announcement>>()
    val archivedItems: LiveData<List<Announcement>> = _archivedItems

    fun fetchProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener {
            it.toObject(User::class.java)?.let { user -> _userProfile.postValue(user) }
        }
    }

    fun archiveItem(id: String, isAlert: Boolean) {
        val coll = if (isAlert) "emergency_alerts" else "announcements"
        db.collection(coll).document(id).update("status", AnnouncementStatus.ARCHIVED)
        db.collection("hazardLocations").document(id).update("active", false)
    }

    fun restoreItem(id: String, isAlert: Boolean) {
        val coll = if (isAlert) "emergency_alerts" else "announcements"
        db.collection(coll).document(id).update("status", AnnouncementStatus.APPROVED)
        db.collection("hazardLocations").document(id).update("active", true)
    }

    fun listenToArchivedItems() {
        // We listen to both announcements and alerts that are archived
        db.collection("announcements")
            .whereEqualTo("status", AnnouncementStatus.ARCHIVED)
            .addSnapshotListener { announcementsValue, _ ->
                db.collection("emergency_alerts")
                    .whereEqualTo("status", AnnouncementStatus.ARCHIVED)
                    .addSnapshotListener { alertsValue, _ ->
                        val list = mutableListOf<Announcement>()
                        announcementsValue?.toObjects(Announcement::class.java)?.let { list.addAll(it) }
                        alertsValue?.toObjects(EmergencyAlert::class.java)?.forEach {
                            list.add(Announcement(
                                id = it.id,
                                title = it.title,
                                content = it.safeContent,
                                type = it.type,
                                status = it.status,
                                authorUid = it.authorUid,
                                timestamp = it.safeTimestamp,
                                affectedAreas = it.affectedAreas,
                                evacuationCenter = it.evacuationCenter
                            ))
                        }
                        _archivedItems.postValue(list.sortedByDescending { it.safeTimestamp })
                    }
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

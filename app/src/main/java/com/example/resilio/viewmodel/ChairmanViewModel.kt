package com.example.resilio.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.User
import com.example.resilio.model.VerificationStatus
import com.example.resilio.model.EmergencyReport
import com.example.resilio.model.ReportStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ChairmanViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _pendingAnnouncements = MutableLiveData<List<Announcement>>()
    val pendingAnnouncements: LiveData<List<Announcement>> = _pendingAnnouncements

    private val _pendingResidents = MutableLiveData<List<User>>()
    val pendingResidents: LiveData<List<User>> = _pendingResidents

    private val _emergencyReports = MutableLiveData<List<EmergencyReport>>()
    val emergencyReports: LiveData<List<EmergencyReport>> = _emergencyReports

    fun listenToEmergencyReports() {
        db.collection("emergency_reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { value, _ ->
                value?.toObjects(EmergencyReport::class.java)?.let { _emergencyReports.postValue(it) }
            }
    }

    fun updateReportStatus(reportId: String, status: ReportStatus) {
        db.collection("emergency_reports").document(reportId).update("status", status)
    }

    fun listenToPendingAnnouncements() {
        db.collection("announcements")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    android.util.Log.e("ChairmanViewModel", "Listen failed.", error)
                    return@addSnapshotListener
                }

                val allAnnouncements = value?.toObjects(Announcement::class.java) ?: emptyList()

                // Filter and sort client-side to avoid index requirement
                val pendingSorted = allAnnouncements
                    .filter { it.status == AnnouncementStatus.PENDING }
                    .sortedByDescending { it.safeTimestamp }

                _pendingAnnouncements.postValue(pendingSorted)
            }
    }

    fun listenToPendingResidents() {
        db.collection("users")
            .whereEqualTo("verificationStatus", VerificationStatus.PENDING)
            .addSnapshotListener { value, _ ->
                value?.toObjects(User::class.java)?.let { _pendingResidents.postValue(it) }
            }
    }

    fun approveAnnouncement(id: String) {
        db.collection("announcements").document(id).update("status", AnnouncementStatus.APPROVED)
    }

    fun approveResident(uid: String) {
        db.collection("users").document(uid).update("verificationStatus", VerificationStatus.APPROVED)
    }

    fun rejectResident(uid: String, reason: String) {
        val updates = mapOf(
            "verificationStatus" to VerificationStatus.REJECTED,
            "rejectionReason" to reason
        )
        db.collection("users").document(uid).update(updates)
    }
}

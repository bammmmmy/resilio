package com.example.resilio.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.resilio.model.Announcement
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BDRRMOViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _myAnnouncements = MutableLiveData<List<Announcement>>()
    val myAnnouncements: LiveData<List<Announcement>> = _myAnnouncements

    fun listenToMyAnnouncements() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("announcements")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    android.util.Log.e("BDRRMOViewModel", "Listen failed.", error)
                    return@addSnapshotListener
                }

                val allAnnouncements = value?.toObjects(Announcement::class.java) ?: emptyList()

                // Filter and sort client-side to avoid index requirement
                val myAnnouncementsSorted = allAnnouncements
                    .filter { it.authorUid == uid }
                    .sortedByDescending { it.timestamp }

                _myAnnouncements.postValue(myAnnouncementsSorted)
            }
    }
}

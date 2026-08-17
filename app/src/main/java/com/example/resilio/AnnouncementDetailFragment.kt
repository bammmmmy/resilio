package com.example.resilio

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.resilio.databinding.FragmentAnnouncementDetailBinding
import com.google.firebase.firestore.FirebaseFirestore

class AnnouncementDetailFragment : Fragment(R.layout.fragment_announcement_detail) {

    private var _binding: FragmentAnnouncementDetailBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAnnouncementDetailBinding.bind(view)

        val title = arguments?.getString("title").orEmpty()
        val content = arguments?.getString("content").orEmpty()
        val authorUid = arguments?.getString("authorUid").orEmpty()
        val affectedAreas = arguments?.getString("affectedAreas").orEmpty()
        val evacuationCenter = arguments?.getString("evacuationCenter").orEmpty()

        binding.tvAnnouncementDetailTitle.text = title
        binding.tvAnnouncementDetailContent.text = content
        binding.tvAnnouncementDetailAuthor.text = "Posted by: Loading..."

        if (authorUid.isNotBlank()) {
            FirebaseFirestore.getInstance().collection("users").document(authorUid).get()
                .addOnSuccessListener { doc ->
                    val name = doc?.getString("fullName").orEmpty().ifEmpty { "Unknown" }
                    binding.tvAnnouncementDetailAuthor.text = "Posted by: $name"
                }
                .addOnFailureListener {
                    binding.tvAnnouncementDetailAuthor.text = "Posted by: Unknown"
                }
        } else {
            binding.tvAnnouncementDetailAuthor.text = "Posted by: Unknown"
        }

        if (affectedAreas.isNotBlank()) {
            binding.tvAnnouncementDetailAffectedAreas.text = "Affected Areas: $affectedAreas"
            binding.tvAnnouncementDetailAffectedAreas.visibility = View.VISIBLE
        }

        if (evacuationCenter.isNotBlank()) {
            binding.tvAnnouncementDetailEvacuationCenter.text = "Evacuation Center: $evacuationCenter"
            binding.tvAnnouncementDetailEvacuationCenter.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

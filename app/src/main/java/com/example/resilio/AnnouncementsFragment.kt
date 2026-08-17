package com.example.resilio

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.resilio.databinding.FragmentAnnouncementsBinding
import com.example.resilio.viewmodel.ResidentViewModel

class AnnouncementsFragment : Fragment(R.layout.fragment_announcements) {

    private var _binding: FragmentAnnouncementsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResidentViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAnnouncementsBinding.bind(view)

        binding.rvAnnouncements.layoutManager = LinearLayoutManager(requireContext())
        binding.tvEmptyAnnouncements.visibility = View.GONE

        viewModel.announcements.observe(viewLifecycleOwner) { list ->
            if (list.isNullOrEmpty()) {
                binding.tvEmptyAnnouncements.visibility = View.VISIBLE
                binding.rvAnnouncements.visibility = View.GONE
            } else {
                binding.tvEmptyAnnouncements.visibility = View.GONE
                binding.rvAnnouncements.visibility = View.VISIBLE
                binding.rvAnnouncements.adapter = AnnouncementAdapter(list, onItemClick = { announcement ->
                    val bundle = Bundle().apply {
                        putString("title", announcement.title)
                        putString("content", announcement.content)
                        putString("authorUid", announcement.authorUid)
                        putString("affectedAreas", announcement.affectedAreas)
                        putString("evacuationCenter", announcement.evacuationCenter)
                    }
                    findNavController().navigate(R.id.action_announcementsFragment_to_announcementDetailFragment, bundle)
                })
            }
        }

        viewModel.listenToAnnouncements()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

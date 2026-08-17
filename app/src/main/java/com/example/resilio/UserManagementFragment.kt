package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.viewmodel.ChairmanViewModel

class UserManagementFragment : Fragment(R.layout.fragment_user_management) {

    private val viewModel: ChairmanViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvPendingResidents)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        
        rv.layoutManager = LinearLayoutManager(requireContext())

        viewModel.pendingResidents.observe(viewLifecycleOwner) { list ->
            if (list.isNullOrEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rv.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rv.visibility = View.VISIBLE
                rv.adapter = ResidentVerificationAdapter(
                    residents = list,
                    onApprove = { uid ->
                        viewModel.approveResident(uid)
                        Toast.makeText(requireContext(), "Resident Verified", Toast.LENGTH_SHORT).show()
                    },
                    onReject = { uid ->
                        Toast.makeText(requireContext(), "Resident Rejected", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        viewModel.listenToPendingResidents()
    }
}

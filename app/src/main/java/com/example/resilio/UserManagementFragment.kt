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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

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
                        showRejectDialog(uid)
                    }
                )
            }
        }

        viewModel.listenToPendingResidents()
    }

    private fun showRejectDialog(uid: String) {
        val input = TextInputEditText(requireContext())
        input.hint = "Reason for rejection"
        val padding = (16 * resources.displayMetrics.density).toInt()
        
        val container = android.widget.FrameLayout(requireContext())
        container.setPadding(padding, padding / 2, padding, 0)
        container.addView(input)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reject Verification")
            .setMessage("Please provide a reason for rejecting this resident.")
            .setView(container)
            .setPositiveButton("Reject") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isNotEmpty()) {
                    viewModel.rejectResident(uid, reason)
                    Toast.makeText(requireContext(), "Resident Rejected", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Reason is required to reject", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

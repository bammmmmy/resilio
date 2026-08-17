package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentLoginBinding
import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import com.example.resilio.model.VerificationStatus
import com.example.resilio.viewmodel.AuthViewModel

class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        viewModel.userState.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            
            result.onSuccess { user ->
                navigateToDashboard(user)
            }.onFailure {
                Toast.makeText(requireContext(), "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (email.isNotEmpty() && password.isNotEmpty()) {
                viewModel.login(email, password)
            }
        }

        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        // Check if user is already logged in to prevent "auto-logout" on app restart
        viewModel.checkAuthState()
    }

    private fun navigateToDashboard(user: User) {
        // Log for debugging: println("Navigating user with role: ${user.role}")
        when (user.role) {
            UserRole.RESIDENT -> {
                // Residence User Redirect
                if (user.uid.startsWith("mock_") || 
                    user.verificationStatus == VerificationStatus.APPROVED ||
                    user.verificationStatus == VerificationStatus.PENDING) {
                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                } else {
                    findNavController().navigate(R.id.action_loginFragment_to_verificationFragment)
                }
            }
            UserRole.BDRRMO -> {
                // BDRRMO Head Redirect
                findNavController().navigate(R.id.action_loginFragment_to_bdrrmoDashboardFragment)
            }
            UserRole.CHAIRMAN -> {
                // Barangay Chairman Redirect
                findNavController().navigate(R.id.action_loginFragment_to_chairmanDashboardFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

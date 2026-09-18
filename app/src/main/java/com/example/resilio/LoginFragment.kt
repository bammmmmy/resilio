package com.example.resilio

import android.os.Bundle
import android.graphics.Rect
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.resilio.databinding.FragmentLoginBinding
import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import com.example.resilio.model.VerificationStatus
import com.example.resilio.viewmodel.AuthViewModel

class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()
    private var imeBottom = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)
        binding.root.setPadding(binding.root.paddingLeft, binding.root.paddingTop, binding.root.paddingRight, 0)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (imeBottom > 0) {
                root.postDelayed({ keepFocusedFieldVisible() }, 100)
            } else {
                root.post { binding.root.smoothScrollTo(0, 0) }
            }
            insets
        }
        binding.etEmail.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.etEmail.postDelayed({ keepFocusedFieldVisible() }, 250) }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.etPassword.postDelayed({ keepFocusedFieldVisible() }, 250) }

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

    private fun keepFocusedFieldVisible() {
        val focusedField = view?.findFocus() ?: return
        val visibleRect = Rect()
        focusedField.getDrawingRect(visibleRect)
        binding.root.offsetDescendantRectToMyCoords(focusedField, visibleRect)
        val visibleBottom = binding.root.height
        val requiredScroll = binding.root.scrollY + visibleRect.bottom - visibleBottom + 32
        if (requiredScroll > binding.root.scrollY) binding.root.smoothScrollTo(0, requiredScroll)
    }

    override fun onDestroyView() {
        binding.root.setPadding(binding.root.paddingLeft, binding.root.paddingTop, binding.root.paddingRight, 0)
        binding.root.smoothScrollTo(0, 0)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, null)
        super.onDestroyView()
        _binding = null
    }
}

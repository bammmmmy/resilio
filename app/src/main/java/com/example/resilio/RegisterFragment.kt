package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentRegisterBinding
import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import com.example.resilio.model.VerificationStatus

import androidx.fragment.app.viewModels
import com.example.resilio.viewmodel.AuthViewModel

class RegisterFragment : Fragment(R.layout.fragment_register) {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()
    /**/
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRegisterBinding.bind(view)

        viewModel.userState.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe

            result.onSuccess {
                Toast.makeText(requireContext(), "Registration successful!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }.onFailure {
                Toast.makeText(requireContext(), "Registration failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val address = binding.etAddress.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            
            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = User(
                fullName = fullName,
                email = email,
                address = address,
                role = UserRole.RESIDENT,
                verificationStatus = VerificationStatus.NOT_SUBMITTED
            )

            viewModel.register(user, password)
        }

        binding.tvLogin.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.resilio

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private var currentUserRole: UserRole = UserRole.RESIDENT
    private lateinit var navController: NavController

    fun getCurrentUserRole(): UserRole = currentUserRole

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupNavigation()
        observeAuthState()
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // Intercept Home navigation to redirect based on role
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    navigateToCorrectHome()
                    true
                }
                else -> {
                    // Default behavior for other tabs
                    NavigationUI.onNavDestinationSelected(item, navController)
                    true
                }
            }
        }

        // Hide BottomNav for certain fragments
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment, R.id.registerFragment, R.id.verificationFragment,
                R.id.createAnnouncementFragment -> {
                    bottomNav.visibility = View.GONE
                }
                else -> {
                    bottomNav.visibility = View.VISIBLE
                    // Update selection if we are on one of the dashboards
                    when (destination.id) {
                        R.id.homeFragment, R.id.bdrrmoDashboardFragment, R.id.chairmanDashboardFragment -> {
                            bottomNav.menu.findItem(R.id.homeFragment).isChecked = true
                        }
                    }
                }
            }
        }
    }

    private fun navigateToCorrectHome() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        when (currentUserRole) {
            UserRole.RESIDENT -> navController.navigate(R.id.homeFragment)
            UserRole.BDRRMO -> navController.navigate(R.id.bdrrmoDashboardFragment)
            UserRole.CHAIRMAN -> navController.navigate(R.id.chairmanDashboardFragment)
        }
    }

    private fun observeAuthState() {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != null) {
                fetchUserRole(uid)
            }
        }
    }

    private fun fetchUserRole(uid: String) {
        // Mock handling
        when (uid) {
            "mock_resident" -> currentUserRole = UserRole.RESIDENT
            "mock_bdrrmo" -> currentUserRole = UserRole.BDRRMO
            "mock_chairman" -> currentUserRole = UserRole.CHAIRMAN
            else -> {
                FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        doc.toObject(User::class.java)?.let {
                            currentUserRole = it.role
                        }
                    }
            }
        }
    }
}

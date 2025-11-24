package com.example.taskify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.taskify.databinding.ActivityMainBinding
import com.example.taskify.ui.fragment.CalendarFragment
import com.example.taskify.ui.fragment.DashboardFragment
import com.example.taskify.ui.fragment.NotesFragment
import com.example.taskify.ui.viewmodel.MainViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkSession()
    }

    private fun checkSession() {
        // 1. Cek User ID di Lokal
        val prefs = getSharedPreferences("taskify_prefs", Context.MODE_PRIVATE)
        val localUserId = prefs.getInt("current_user_id", -1)

        // 2. Cek Firebase
        val firebaseUser = FirebaseAuth.getInstance().currentUser

        if (localUserId != -1) {
            // Session Lokal Aman -> Masuk
            initUI()
        } else if (firebaseUser != null) {
            // Session Lokal Hilang, tapi Firebase Login -> Coba Recover
            // Disini kita kirim email dari firebaseUser yang sedang aktif
            val email = firebaseUser.email ?: ""
            if (email.isNotEmpty()) {
                recoverSession(email)
            } else {
                performLogout()
            }
        } else {
            // Tidak ada session -> Login
            goToLogin()
        }
    }

    private fun recoverSession(email: String) {
        lifecycleScope.launch {
            // Minta ViewModel mencari User ID berdasarkan email
            val isRecovered = viewModel.recoverSession(email)
            if (isRecovered) {
                initUI() // Sukses recover
            } else {
                // Gagal recover (Data lokal rusak/hilang total) -> Logout paksa
                performLogout()
            }
        }
    }

    private fun initUI() {
        // Memuat tampilan jika login valid
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        setupBottomNavigation()

        // Load default fragment
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            loadFragment(DashboardFragment())
            supportActionBar?.title = "Daftar Tugas"
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                performLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performLogout() {
        viewModel.logout()
        goToLogin()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(DashboardFragment())
                    supportActionBar?.title = "Daftar Tugas"
                    true
                }
                R.id.nav_calendar -> {
                    loadFragment(CalendarFragment())
                    supportActionBar?.title = "Kalender"
                    true
                }
                R.id.nav_notes -> {
                    loadFragment(NotesFragment())
                    supportActionBar?.title = "Catatan"
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
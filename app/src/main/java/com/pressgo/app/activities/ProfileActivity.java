package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.pressgo.app.databinding.ActivityProfileBinding;
import com.pressgo.app.models.User;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.SessionManager;

public class ProfileActivity extends AppCompatActivity {
    private static final String TAG = "ProfileActivity";

    private ActivityProfileBinding binding;
    private UserRepository userRepository;
    private SessionManager sessionManager;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userRepository = UserRepository.getInstance();
        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("User Profile");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupListeners();
        loadProfile();
    }

    private void setupListeners() {
        binding.btnSaveProfile.setOnClickListener(v -> saveProfile());
        binding.btnLogout.setOnClickListener(v -> confirmLogout());
        binding.btnChangePassword.setOnClickListener(v -> {
            if (currentUser != null && currentUser.getEmail() != null) {
                userRepository.sendPasswordReset(currentUser.getEmail(), task -> {
                    Toast.makeText(this, "Password reset email sent to " + currentUser.getEmail(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadProfile() {
        binding.progressBar.setVisibility(View.VISIBLE);
        userRepository.getCurrentUserProfile(user -> {
            binding.progressBar.setVisibility(View.GONE);
            if (user != null) {
                currentUser = user;
                binding.etName.setText(user.getDisplayName());
                binding.etEmail.setText(user.getEmail());
                binding.etPhone.setText(user.getPhone());
                binding.tvRoleBadge.setText("Role: " + user.getRole());
            }
        }, e -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
        });
    }

    private void saveProfile() {
        if (currentUser == null) return;
        String name = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
        String phone = binding.etPhone.getText() != null ? binding.etPhone.getText().toString().trim() : "";

        currentUser.setDisplayName(name);
        currentUser.setPhone(phone);

        binding.progressBar.setVisibility(View.VISIBLE);
        userRepository.updateUserProfile(currentUser, task -> {
            binding.progressBar.setVisibility(View.GONE);
            if (task.isSuccessful()) {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of PressGo?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    userRepository.logout();
                    sessionManager.clearSession();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}

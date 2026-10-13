package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.pressgo.app.databinding.ActivityRegisterBinding;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.SessionManager;
import com.pressgo.app.utils.ValidationUtils;

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "RegisterActivity";

    private ActivityRegisterBinding binding;
    private UserRepository userRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userRepository = UserRepository.getInstance();
        sessionManager = new SessionManager(this);

        setupListeners();
    }

    private void setupListeners() {
        binding.btnRegister.setOnClickListener(v -> attemptRegister());

        binding.tvLogin.setOnClickListener(v -> finish());

        binding.tvGoToShopkeeperRegister.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, AdminRegisterActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void attemptRegister() {
        String name = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String phone = binding.etPhone.getText() != null ? binding.etPhone.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";
        String confirmPassword = binding.etConfirmPassword.getText() != null ? binding.etConfirmPassword.getText().toString() : "";

        if (!ValidationUtils.isValidName(name)) {
            binding.tilName.setError("Name must be at least 2 characters");
            return;
        } else {
            binding.tilName.setError(null);
        }

        if (!ValidationUtils.isValidEmail(email)) {
            binding.tilEmail.setError("Please enter a valid email address");
            return;
        } else {
            binding.tilEmail.setError(null);
        }

        if (!ValidationUtils.isValidPhone(phone)) {
            binding.tilPhone.setError("Please enter a valid phone number");
            return;
        } else {
            binding.tilPhone.setError(null);
        }

        if (!ValidationUtils.isValidPassword(password)) {
            binding.tilPassword.setError("Password must be at least 8 characters");
            return;
        } else {
            binding.tilPassword.setError(null);
        }

        if (!password.equals(confirmPassword)) {
            binding.tilConfirmPassword.setError("Passwords do not match");
            return;
        } else {
            binding.tilConfirmPassword.setError(null);
        }

        showLoading(true);

        userRepository.registerUser(email, password, name, phone, task -> {
            showLoading(false);
            if (task.isSuccessful()) {
                Log.d(TAG, "Registration successful");
                if (userRepository.getCurrentUser() != null) {
                    sessionManager.saveUserSession(userRepository.getCurrentUser().getUid(), Constants.ROLE_CUSTOMER, null);
                }
                Intent intent = new Intent(RegisterActivity.this, CustomerDashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                Exception e = task.getException();
                String message = "Registration failed";
                if (e instanceof FirebaseAuthUserCollisionException) {
                    message = "An account with this email already exists";
                } else if (e != null) {
                    message = e.getLocalizedMessage();
                }
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!loading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}

package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.pressgo.app.databinding.ActivityLoginBinding;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.SessionManager;
import com.pressgo.app.utils.ValidationUtils;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private ActivityLoginBinding binding;
    private UserRepository userRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userRepository = UserRepository.getInstance();
        sessionManager = new SessionManager(this);

        setupListeners();
    }

    private void setupListeners() {
        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        binding.tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        binding.tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        binding.btnGoToAdminLogin.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, AdminLoginActivity.class);
            startActivity(intent);
        });

        binding.tvLoginSubtitle.setOnClickListener(v -> {
            binding.etEmail.setText("student99@test.com");
            binding.etPassword.setText("Password123");
        });

        binding.tvLoginSubtitle.setOnLongClickListener(v -> {
            sessionManager.saveUserSession("demo_cust_uid", Constants.ROLE_CUSTOMER, null);
            Intent intent = new Intent(LoginActivity.this, CustomerDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        });
    }

    private void attemptLogin() {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

        if (!ValidationUtils.isValidEmail(email)) {
            binding.tilEmail.setError("Please enter a valid email address");
            return;
        } else {
            binding.tilEmail.setError(null);
        }

        if (password.isEmpty()) {
            binding.tilPassword.setError("Please enter your password");
            return;
        } else {
            binding.tilPassword.setError(null);
        }

        showLoading(true);

        userRepository.loginUser(email, password, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Login successful, fetching user profile");
                fetchUserProfileAndNavigate();
            } else {
                showLoading(false);
                Exception e = task.getException();
                String message = "Authentication failed";
                if (e instanceof FirebaseAuthInvalidUserException) {
                    message = "No account found with this email";
                } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    message = "Invalid email or password";
                } else if (e != null) {
                    message = e.getLocalizedMessage();
                }
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void fetchUserProfileAndNavigate() {
        userRepository.getCurrentUserProfile(user -> {
            showLoading(false);
            if (user != null) {
                String role = user.getRole();
                // STRICT SEPARATION: Only Customers can log in through the Customer Login page
                if (Constants.ROLE_ADMIN.equals(role) || Constants.ROLE_STAFF.equals(role)) {
                    Log.w(TAG, "Shopkeeper login blocked on Customer Login page: role=" + role);
                    userRepository.logout();
                    sessionManager.clearSession();
                    Snackbar.make(binding.getRoot(),
                            "Access Denied: This is the Customer Login page. Shopkeepers must log in through the Shopkeeper Portal.",
                            Snackbar.LENGTH_INDEFINITE)
                            .setAction("Go to Shopkeeper Login", v -> {
                                Intent intent = new Intent(LoginActivity.this, AdminLoginActivity.class);
                                startActivity(intent);
                            })
                            .show();
                    return;
                }

                sessionManager.saveUserSession(user.getUid(), Constants.ROLE_CUSTOMER, user.getShopId());
                navigateToCustomerHome();
            } else {
                // Default to customer
                sessionManager.saveUserSession(userRepository.getCurrentUser().getUid(), Constants.ROLE_CUSTOMER, null);
                navigateToCustomerHome();
            }
        }, e -> {
            showLoading(false);
            Log.e(TAG, "Failed to fetch user profile", e);
            // Default to customer dashboard
            navigateToCustomerHome();
        });
    }

    private void navigateToCustomerHome() {
        Intent intent = new Intent(LoginActivity.this, CustomerDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!loading);
        binding.etEmail.setEnabled(!loading);
        binding.etPassword.setEnabled(!loading);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}

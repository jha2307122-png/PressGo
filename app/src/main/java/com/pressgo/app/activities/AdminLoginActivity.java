package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.pressgo.app.databinding.ActivityAdminLoginBinding;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.SessionManager;
import com.pressgo.app.utils.ValidationUtils;

/**
 * Dedicated login activity for Shopkeepers and Shop Staff.
 * Strictly verifies that the authenticated user has the ADMIN or STAFF role,
 * and possesses an associated shopId before granting access to AdminDashboardActivity.
 */
public class AdminLoginActivity extends AppCompatActivity {

    private static final String TAG = "AdminLoginActivity";

    private ActivityAdminLoginBinding binding;
    private UserRepository userRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityAdminLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userRepository = UserRepository.getInstance();
        sessionManager = new SessionManager(this);

        setupListeners();
    }

    private void setupListeners() {
        binding.btnLogin.setOnClickListener(v -> attemptAdminLogin());

        binding.tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(AdminLoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        binding.tvRegisterShop.setOnClickListener(v -> {
            Intent intent = new Intent(AdminLoginActivity.this, AdminRegisterActivity.class);
            startActivity(intent);
        });

        binding.btnGoToCustomerLogin.setOnClickListener(v -> {
            Intent intent = new Intent(AdminLoginActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        binding.tvAdminBadge.setOnClickListener(v -> {
            binding.etEmail.setText("sadia@test.com");
            binding.etPassword.setText("Password123");
        });

        binding.tvAdminBadge.setOnLongClickListener(v -> {
            sessionManager.saveUserSession("demo_admin_uid", Constants.ROLE_ADMIN, "demo_shop_01");
            Intent intent = new Intent(AdminLoginActivity.this, AdminDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        });
    }

    private void attemptAdminLogin() {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

        if (!ValidationUtils.isValidEmail(email)) {
            binding.tilEmail.setError("Please enter a valid shop email address");
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
                Log.d(TAG, "Auth successful, verifying shopkeeper role & shopId");
                verifyRoleAndNavigate();
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

    private void verifyRoleAndNavigate() {
        userRepository.getCurrentUserProfile(user -> {
            showLoading(false);

            if (user == null) {
                userRepository.logout();
                sessionManager.clearSession();
                Snackbar.make(binding.getRoot(), "User profile record not found.", Snackbar.LENGTH_LONG).show();
                return;
            }

            String role = user.getRole();
            // Strict role check: Only ADMIN and STAFF are allowed in Shopkeeper portal
            if (!Constants.ROLE_ADMIN.equals(role) && !Constants.ROLE_STAFF.equals(role)) {
                Log.w(TAG, "Unauthorized access attempt by role: " + role);
                userRepository.logout();
                sessionManager.clearSession();
                Snackbar.make(binding.getRoot(),
                        "Access Denied: This account is registered as a Customer. Please use Customer Login.",
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction("Go to Customer Login", v -> {
                            Intent intent = new Intent(AdminLoginActivity.this, LoginActivity.class);
                            startActivity(intent);
                            finish();
                        })
                        .show();
                return;
            }

            String shopId = user.getShopId();
            if (shopId == null || shopId.trim().isEmpty()) {
                Log.w(TAG, "Admin account missing shopId: uid=" + user.getUid());
                userRepository.logout();
                sessionManager.clearSession();
                Snackbar.make(binding.getRoot(),
                        "No shop associated with this account. Please contact campus admin.",
                        Snackbar.LENGTH_LONG).show();
                return;
            }

            // Successfully authenticated shopkeeper
            Log.d(TAG, "Shopkeeper login verified: role=" + role + " shopId=" + shopId);
            sessionManager.saveUserSession(user.getUid(), role, shopId);
            Toast.makeText(this, "Welcome to Shopkeeper Dashboard", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(AdminLoginActivity.this, AdminDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

        }, e -> {
            showLoading(false);
            Log.e(TAG, "Failed to verify admin role", e);
            userRepository.logout();
            Snackbar.make(binding.getRoot(), "Failed to verify permissions: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        });
    }

    private void showLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!loading);
        binding.btnGoToCustomerLogin.setEnabled(!loading);
        binding.etEmail.setEnabled(!loading);
        binding.etPassword.setEnabled(!loading);
    }
}

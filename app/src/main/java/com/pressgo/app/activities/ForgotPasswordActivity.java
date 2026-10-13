package com.pressgo.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.pressgo.app.databinding.ActivityForgotPasswordBinding;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.ValidationUtils;

public class ForgotPasswordActivity extends AppCompatActivity {
    private static final String TAG = "ForgotPasswordActivity";

    private ActivityForgotPasswordBinding binding;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userRepository = UserRepository.getInstance();

        binding.btnSendReset.setOnClickListener(v -> sendResetEmail());
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void sendResetEmail() {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";

        if (!ValidationUtils.isValidEmail(email)) {
            binding.tilEmail.setError("Please enter a valid email address");
            return;
        } else {
            binding.tilEmail.setError(null);
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSendReset.setEnabled(false);

        userRepository.sendPasswordReset(email, task -> {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnSendReset.setEnabled(true);

            if (task.isSuccessful()) {
                Toast.makeText(ForgotPasswordActivity.this, "Password reset link sent to your email", Toast.LENGTH_LONG).show();
                finish();
            } else {
                String error = task.getException() != null ? task.getException().getLocalizedMessage() : "Failed to send reset email";
                Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}

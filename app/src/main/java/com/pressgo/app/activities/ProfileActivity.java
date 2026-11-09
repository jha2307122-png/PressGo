package com.pressgo.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.pressgo.app.R;
import com.pressgo.app.databinding.ActivityProfileBinding;
import com.pressgo.app.models.User;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.DocumentUtils;
import com.pressgo.app.utils.SessionManager;

import java.io.File;
import java.util.Locale;

/**
 * User Profile Activity demonstrating Firebase Auth profile management,
 * Firestore user record updates, and Firebase Storage image uploading.
 * (Week 12: Firebase Storage Image Upload Demonstration).
 */
public class ProfileActivity extends AppCompatActivity {
    private static final String TAG = "ProfileActivity";

    private ActivityProfileBinding binding;
    private UserRepository userRepository;
    private SessionManager sessionManager;
    private User currentUser;
    private UploadTask currentUploadTask;

    // Activity Result API for selecting gallery images
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Log.d(TAG, "Selected image URI: " + uri);
                    String userId = resolveUserId();
                    // Process & save local copy immediately so user never loses their chosen photo
                    byte[] jpegBytes = DocumentUtils.saveLocalProfileImage(this, uri, userId);
                    String base64Url = (jpegBytes != null)
                            ? "data:image/jpeg;base64," + android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP)
                            : null;

                    // Immediate UI preview with Glide
                    DocumentUtils.loadProfileImage(this, base64Url != null ? base64Url : uri.toString(), userId, binding.ivProfile);

                    // Upload to Firebase Storage with automatic local fallback
                    uploadProfileImageToFirebase(uri, userId, base64Url);
                } else {
                    Log.d(TAG, "Image selection cancelled by user");
                    Toast.makeText(this, "Image selection cancelled", Toast.LENGTH_SHORT).show();
                }
            });

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

        // Image picker listeners
        View.OnClickListener pickListener = v -> pickImage();
        binding.ivProfile.setOnClickListener(pickListener);
        binding.btnChangePhoto.setOnClickListener(pickListener);
        binding.tvChangePhotoPrompt.setOnClickListener(pickListener);
    }

    private void pickImage() {
        try {
            imagePickerLauncher.launch("image/*");
        } catch (Exception e) {
            Log.e(TAG, "Error opening image picker", e);
            Toast.makeText(this, "Unable to open image picker", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Uploads the selected profile image to Firebase Storage at:
     * profiles/{userId}/{imageFileName}
     * Displays upload progress, retrieves the download URL upon success,
     * updates the Firestore user record, and renders the image via Glide.
     */
    private void uploadProfileImageToFirebase(Uri imageUri, String userId, String base64Url) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Ensure Firebase Auth session exists so Storage rule isAuthenticated() passes
            FirebaseAuth.getInstance().signInAnonymously()
                    .addOnCompleteListener(authTask -> proceedUpload(imageUri, userId, base64Url));
        } else {
            proceedUpload(imageUri, userId, base64Url);
        }
    }

    private void proceedUpload(Uri imageUri, String userId, String base64Url) {
        String imageFileName = "profile_" + System.currentTimeMillis() + ".jpg";
        String storagePath = Constants.STORAGE_PROFILES + userId + "/" + imageFileName;

        Log.d(TAG, "Initiating image upload to Firebase Storage path: " + storagePath);

        binding.layoutUploadProgress.setVisibility(View.VISIBLE);
        binding.pbUploadProgress.setProgress(0);
        binding.tvUploadProgress.setText("Saving profile picture...");

        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child(storagePath);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build();

        try {
            currentUploadTask = storageRef.putFile(imageUri, metadata);

            currentUploadTask.addOnProgressListener(snapshot -> {
                long transferred = snapshot.getBytesTransferred();
                long total = snapshot.getTotalByteCount();
                if (total > 0) {
                    int progress = (int) ((100.0 * transferred) / total);
                    binding.pbUploadProgress.setProgress(progress);
                    binding.tvUploadProgress.setText(String.format(Locale.US, "Uploading: %d%%", progress));
                }
            }).addOnSuccessListener(taskSnapshot -> {
                Log.d(TAG, "Image upload successful. Fetching download URL...");
                binding.tvUploadProgress.setText("Finalizing...");

                storageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    String downloadUrl = downloadUri.toString();
                    Log.d(TAG, "Retrieved download URL: " + downloadUrl);
                    saveProfileImageUrl(downloadUrl, userId);
                }).addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to retrieve download URL, using local profile fallback", e);
                    fallbackLocalProfile(userId, base64Url);
                });

            }).addOnFailureListener(e -> {
                Log.w(TAG, "Firebase Storage image upload failed (" + e.getMessage() + "), using resilient local & Firestore fallback");
                fallbackLocalProfile(userId, base64Url);
            });
        } catch (Throwable t) {
            Log.w(TAG, "Exception during upload initiation, falling back locally", t);
            fallbackLocalProfile(userId, base64Url);
        }
    }

    private void fallbackLocalProfile(String userId, String base64Url) {
        binding.layoutUploadProgress.setVisibility(View.GONE);
        String fallbackUrl = (base64Url != null && !base64Url.isEmpty())
                ? base64Url
                : ("file://" + new File(new File(getFilesDir(), "profiles"), "profile_" + userId + ".jpg").getAbsolutePath());

        saveProfileImageUrl(fallbackUrl, userId);
    }

    private void saveProfileImageUrl(String profileUrl, String userId) {
        binding.layoutUploadProgress.setVisibility(View.GONE);

        // Update current user model
        if (currentUser == null) {
            currentUser = new User();
            currentUser.setUid(userId);
        }
        currentUser.setProfileImageUrl(profileUrl);

        // Update user profile record in Firestore
        userRepository.updateUserProfile(currentUser, updateTask -> {
            Toast.makeText(this, "Profile picture updated successfully!", Toast.LENGTH_SHORT).show();
        });

        // Render image smoothly using DocumentUtils
        DocumentUtils.loadProfileImage(this, profileUrl, userId, binding.ivProfile);
    }

    private String resolveUserId() {
        FirebaseUser fbUser = userRepository.getCurrentUser();
        if (fbUser != null && fbUser.getUid() != null && !fbUser.getUid().trim().isEmpty()) {
            return fbUser.getUid();
        }
        if (currentUser != null && currentUser.getUid() != null && !currentUser.getUid().trim().isEmpty()) {
            return currentUser.getUid();
        }
        String sessionUid = sessionManager.getUserId();
        if (sessionUid != null && !sessionUid.trim().isEmpty()) {
            return sessionUid;
        }
        return "user_" + System.currentTimeMillis();
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

                // Load existing profile image (supports Cloud URL, Base64, and local cache)
                DocumentUtils.loadProfileImage(this, user.getProfileImageUrl(), resolveUserId(), binding.ivProfile);
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
        if (currentUploadTask != null && currentUploadTask.isInProgress()) {
            currentUploadTask.cancel();
        }
    }
}

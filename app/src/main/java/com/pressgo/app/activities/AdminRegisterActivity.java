package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.pressgo.app.databinding.ActivityAdminRegisterBinding;
import com.pressgo.app.models.Shop;
import com.pressgo.app.models.ShopPricing;
import com.pressgo.app.models.User;
import com.pressgo.app.repositories.ShopRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.SessionManager;
import com.pressgo.app.utils.ValidationUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.Arrays;
import java.util.Locale;

/**
 * Registration activity for Shopkeepers.
 * Creates an authorized ADMIN user account and simultaneously registers
 * the corresponding campus shop and default pricing rate-card in Firestore.
 */
public class AdminRegisterActivity extends AppCompatActivity {

    private static final String TAG = "AdminRegisterActivity";

    private ActivityAdminRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SessionManager sessionManager;

    private double shopLatitude = 23.7808;
    private double shopLongitude = 90.4192;
    private boolean hasPickedLocation = false;

    private final ActivityResultLauncher<Intent> mapPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    shopLatitude = result.getData().getDoubleExtra(ShopMapActivity.EXTRA_LATITUDE, 23.7808);
                    shopLongitude = result.getData().getDoubleExtra(ShopMapActivity.EXTRA_LONGITUDE, 90.4192);
                    hasPickedLocation = true;
                    binding.btnPickShopLocation.setText("📍 Change Map Location");
                    binding.tvLocationPreview.setText(String.format(Locale.getDefault(), "Location pinned: %.5f, %.5f", shopLatitude, shopLongitude));
                    binding.tvLocationPreview.setTextColor(0xFF2E7D32);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityAdminRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);

        setupListeners();
    }

    private void setupListeners() {
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvLogin.setOnClickListener(v -> finish());

        binding.btnPickShopLocation.setOnClickListener(v -> {
            String name = binding.etShopName.getText() != null ? binding.etShopName.getText().toString().trim() : "My Shop";
            String addr = binding.etShopAddress.getText() != null ? binding.etShopAddress.getText().toString().trim() : "Campus";
            Intent intent = new Intent(this, ShopMapActivity.class);
            intent.putExtra(ShopMapActivity.EXTRA_MODE, ShopMapActivity.MODE_PICKER);
            intent.putExtra(ShopMapActivity.EXTRA_LATITUDE, shopLatitude);
            intent.putExtra(ShopMapActivity.EXTRA_LONGITUDE, shopLongitude);
            intent.putExtra(ShopMapActivity.EXTRA_SHOP_NAME, name);
            intent.putExtra(ShopMapActivity.EXTRA_SHOP_ADDRESS, addr);
            mapPickerLauncher.launch(intent);
        });
    }

    private void attemptRegister() {
        String ownerName = binding.etOwnerName.getText() != null ? binding.etOwnerName.getText().toString().trim() : "";
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String phone = binding.etPhone.getText() != null ? binding.etPhone.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";
        String confirmPassword = binding.etConfirmPassword.getText() != null ? binding.etConfirmPassword.getText().toString() : "";

        String shopName = binding.etShopName.getText() != null ? binding.etShopName.getText().toString().trim() : "";
        String shopAddress = binding.etShopAddress.getText() != null ? binding.etShopAddress.getText().toString().trim() : "";
        String shopPhone = binding.etShopPhone.getText() != null ? binding.etShopPhone.getText().toString().trim() : "";

        // Validations
        if (!ValidationUtils.isValidName(ownerName)) {
            binding.tilOwnerName.setError("Owner name must be at least 2 characters");
            return;
        } else {
            binding.tilOwnerName.setError(null);
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

        if (shopName.length() < 2) {
            binding.tilShopName.setError("Shop name must be at least 2 characters");
            return;
        } else {
            binding.tilShopName.setError(null);
        }

        if (shopAddress.length() < 3) {
            binding.tilShopAddress.setError("Please specify shop location (e.g. Building, Room)");
            return;
        } else {
            binding.tilShopAddress.setError(null);
        }

        showLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        showLoading(false);
                        Snackbar.make(binding.getRoot(), "Failed to create user account", Snackbar.LENGTH_LONG).show();
                        return;
                    }

                    String uid = firebaseUser.getUid();
                    String finalShopPhone = shopPhone.isEmpty() ? phone : shopPhone;
                    createShopAndAdminProfile(uid, email, ownerName, phone, shopName, shopAddress, finalShopPhone);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Registration failed", e);
                    String message = "Registration failed";
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        message = "An account already exists with this email";
                    } else if (e.getMessage() != null) {
                        message = e.getMessage();
                    }
                    Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                });
    }

    private void createShopAndAdminProfile(String uid,
                                          String email,
                                          String ownerName,
                                          String ownerPhone,
                                          String shopName,
                                          String shopAddress,
                                          String shopPhone) {

        // Generate unique Shop ID
        String shopId = db.collection(Constants.COLLECTION_SHOPS).document().getId();

        // 1. Create Shop Document
        Shop shop = new Shop();
        shop.setId(shopId);
        shop.setName(shopName);
        shop.setAddress(shopAddress);
        shop.setPhone(shopPhone);
        shop.setEmail(email);
        shop.setDescription("Campus printing and photocopying services");
        shop.setOwnerId(uid);
        shop.setOpen(true);
        shop.setActive(true);
        shop.setCreatedAt(Timestamp.now());
        shop.setSupportedFileTypes(Arrays.asList("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "jpg", "jpeg", "png"));
        shop.setAvailablePaperSizes(Arrays.asList("A4", "A3", "Letter"));
        shop.setHasColorPrinting(true);
        shop.setHasDuplexPrinting(true);
        shop.setHasBinding(true);
        shop.setHasStapling(true);
        shop.setHasLamination(false);
        shop.setRating(5.0);
        shop.setTotalRatings(0);
        shop.setEstimatedQueueMinutes(5);
        shop.setLatitude(shopLatitude);
        shop.setLongitude(shopLongitude);

        // 2. Create User Document with ADMIN role & shopId
        User user = new User(
                uid,
                email,
                ownerName,
                ownerPhone,
                Constants.ROLE_ADMIN,
                shopId,
                null,
                Timestamp.now(),
                true
        );

        // 3. Create default Shop Pricing Card
        ShopPricing pricing = new ShopPricing();
        pricing.setId(shopId);
        pricing.setShopId(shopId);
        pricing.setBwPageRate(0.10);
        pricing.setColorPageRate(0.50);
        pricing.setA4Multiplier(1.0);
        pricing.setA3Multiplier(1.5);
        pricing.setLetterMultiplier(1.0);
        pricing.setDuplexDiscount(0.02);
        pricing.setBindingCharge(1.50);
        pricing.setStaplingCharge(0.25);
        pricing.setLaminationCharge(1.00);
        pricing.setPriorityCharge(2.00);
        pricing.setMinimumOrder(0.50);
        pricing.setUpdatedAt(Timestamp.now());

        // Save to local cache immediately so shop is instantly accessible across the app
        ShopRepository.getInstance().saveLocalShop(shop);
        ShopRepository.getInstance().saveLocalPricing(pricing);

        // Batch write to Firestore
        WriteBatch batch = db.batch();
        batch.set(db.collection(Constants.COLLECTION_SHOPS).document(shopId), shop.toMap());
        batch.set(db.collection(Constants.COLLECTION_USERS).document(uid), user.toMap());
        batch.set(db.collection("shopPricing").document(shopId), pricing.toMap());
        batch.set(db.collection("shop_pricing").document(shopId), pricing.toMap());

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Log.d(TAG, "Shop and Admin profile created successfully for uid=" + uid + " shopId=" + shopId);
                    sessionManager.saveUserSession(uid, Constants.ROLE_ADMIN, shopId);
                    Toast.makeText(this, "Shop registered successfully! Welcome.", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(AdminRegisterActivity.this, AdminDashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Batch write failed, proceeding with local registration", e);
                    // Even if cloud write fails/delays, local session & shop cache are saved!
                    sessionManager.saveUserSession(uid, Constants.ROLE_ADMIN, shopId);
                    Toast.makeText(this, "Shop registered locally! Welcome.", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(AdminRegisterActivity.this, AdminDashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    private void showLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!loading);
        binding.etOwnerName.setEnabled(!loading);
        binding.etEmail.setEnabled(!loading);
        binding.etPhone.setEnabled(!loading);
        binding.etPassword.setEnabled(!loading);
        binding.etConfirmPassword.setEnabled(!loading);
        binding.etShopName.setEnabled(!loading);
        binding.etShopAddress.setEnabled(!loading);
        binding.etShopPhone.setEnabled(!loading);
    }
}

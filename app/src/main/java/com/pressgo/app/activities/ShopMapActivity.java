package com.pressgo.app.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.pressgo.app.databinding.ActivityShopMapBinding;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Interactive map activity supporting both:
 * 1. MODE_VIEW (Customers): View shop pin on campus map and launch Google Maps directions.
 * 2. MODE_PICKER (Shopkeeper): Tap or drag on the interactive map to pin shop coordinates.
 */
public class ShopMapActivity extends AppCompatActivity {

    private static final String TAG = "ShopMapActivity";

    public static final String EXTRA_MODE         = "extra_mode";
    public static final String MODE_VIEW          = "mode_view";
    public static final String MODE_PICKER        = "mode_picker";

    public static final String EXTRA_LATITUDE     = "extra_latitude";
    public static final String EXTRA_LONGITUDE    = "extra_longitude";
    public static final String EXTRA_SHOP_NAME    = "extra_shop_name";
    public static final String EXTRA_SHOP_ADDRESS = "extra_shop_address";
    public static final String EXTRA_SHOP_ID      = "extra_shop_id";

    // Default campus coordinates (fallback if 0.0)
    private static final double DEFAULT_LAT = 23.7808;
    private static final double DEFAULT_LNG = 90.4192;

    private ActivityShopMapBinding binding;
    private String mode = MODE_VIEW;

    private double selectedLat = DEFAULT_LAT;
    private double selectedLng = DEFAULT_LNG;
    private String shopName = "Campus Print Shop";
    private String shopAddress = "Campus Location";
    private String shopId = null;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    centerOnUserLocation();
                } else {
                    Toast.makeText(this, "Location permission is required to detect your GPS position", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityShopMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        parseIntentExtras();
        setupToolbar();
        setupUI();
        initWebViewMap();
    }

    private void parseIntentExtras() {
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_MODE)) {
                mode = intent.getStringExtra(EXTRA_MODE);
            }
            double lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0);
            double lng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0);
            if (lat != 0.0 && lng != 0.0) {
                selectedLat = lat;
                selectedLng = lng;
            }
            if (intent.hasExtra(EXTRA_SHOP_NAME)) {
                shopName = intent.getStringExtra(EXTRA_SHOP_NAME);
            }
            if (intent.hasExtra(EXTRA_SHOP_ADDRESS)) {
                shopAddress = intent.getStringExtra(EXTRA_SHOP_ADDRESS);
            }
            if (intent.hasExtra(EXTRA_SHOP_ID)) {
                shopId = intent.getStringExtra(EXTRA_SHOP_ID);
            }
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(MODE_PICKER.equals(mode) ? "Set Shop Location" : "Shop Location");
        }
    }

    private void setupUI() {
        if (MODE_PICKER.equals(mode)) {
            binding.layoutCustomerView.setVisibility(View.GONE);
            binding.layoutShopkeeperPicker.setVisibility(View.VISIBLE);
            binding.tvCoordinates.setText(String.format(Locale.getDefault(), "Coordinates: %.5f, %.5f", selectedLat, selectedLng));

            binding.btnConfirmLocation.setOnClickListener(v -> confirmPickerLocation());
        } else {
            binding.layoutCustomerView.setVisibility(View.VISIBLE);
            binding.layoutShopkeeperPicker.setVisibility(View.GONE);
            binding.tvShopName.setText(shopName);
            binding.tvShopAddress.setText(shopAddress);

            binding.btnGetDirections.setOnClickListener(v -> openDirectionsInGoogleMaps());
        }

        binding.fabMyLocation.setOnClickListener(v -> checkPermissionAndCenterLocation());
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void initWebViewMap() {
        binding.mapProgressBar.setVisibility(View.VISIBLE);

        WebSettings settings = binding.webViewMap.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setLoadsImagesAutomatically(true);

        binding.webViewMap.addJavascriptInterface(new MapBridgeInterface(), "AndroidBridge");

        binding.webViewMap.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                binding.mapProgressBar.setVisibility(View.GONE);
            }
        });

        binding.webViewMap.setWebChromeClient(new WebChromeClient());

        // Load interactive OpenStreetMap + Leaflet HTML
        String html = generateMapHtml(selectedLat, selectedLng, shopName, shopAddress, MODE_PICKER.equals(mode));
        binding.webViewMap.loadDataWithBaseURL("https://pressgo.local", html, "text/html", "UTF-8", null);
    }

    private String generateMapHtml(double lat, double lng, String name, String address, boolean isPicker) {
        String escapedName = name.replace("\"", "\\\"");
        String escapedAddress = address.replace("\"", "\\\"");

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\" />\n" +
                "    <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />\n" +
                "    <script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n" +
                "    <style>\n" +
                "        body, html, #map { margin: 0; padding: 0; height: 100%; width: 100%; background: #ECEFF1; }\n" +
                "        .leaflet-popup-content-wrapper { border-radius: 12px; font-family: sans-serif; box-shadow: 0 4px 12px rgba(0,0,0,0.2); }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id=\"map\"></div>\n" +
                "    <script>\n" +
                "        var isPicker = " + (isPicker ? "true" : "false") + ";\n" +
                "        var initialLat = " + lat + ";\n" +
                "        var initialLng = " + lng + ";\n" +
                "        var shopName = \"" + escapedName + "\";\n" +
                "        var shopAddress = \"" + escapedAddress + "\";\n" +
                "\n" +
                "        var map = L.map('map', { zoomControl: false }).setView([initialLat, initialLng], 16);\n" +
                "        L.control.zoom({ position: 'topright' }).addTo(map);\n" +
                "\n" +
                "        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {\n" +
                "            maxZoom: 19,\n" +
                "            attribution: '© OpenStreetMap contributors'\n" +
                "        }).addTo(map);\n" +
                "\n" +
                "        var marker = L.marker([initialLat, initialLng], { draggable: isPicker }).addTo(map);\n" +
                "\n" +
                "        if (!isPicker) {\n" +
                "            marker.bindPopup(\"<div style='font-size:14px;'><b>\" + shopName + \"</b><br><span style='color:#666;'>\" + shopAddress + \"</span></div>\").openPopup();\n" +
                "        }\n" +
                "\n" +
                "        if (isPicker) {\n" +
                "            marker.on('dragend', function(e) {\n" +
                "                var pos = marker.getLatLng();\n" +
                "                if (window.AndroidBridge) {\n" +
                "                    window.AndroidBridge.onLocationSelected(pos.lat, pos.lng);\n" +
                "                }\n" +
                "            });\n" +
                "\n" +
                "            map.on('click', function(e) {\n" +
                "                marker.setLatLng(e.latlng);\n" +
                "                if (window.AndroidBridge) {\n" +
                "                    window.AndroidBridge.onLocationSelected(e.latlng.lat, e.latlng.lng);\n" +
                "                }\n" +
                "            });\n" +
                "        }\n" +
                "\n" +
                "        function setMapCenter(cLat, cLng, zoom) {\n" +
                "            map.setView([cLat, cLng], zoom || 16);\n" +
                "            if (marker) {\n" +
                "                marker.setLatLng([cLat, cLng]);\n" +
                "            }\n" +
                "            if (isPicker && window.AndroidBridge) {\n" +
                "                window.AndroidBridge.onLocationSelected(cLat, cLng);\n" +
                "            }\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    private void checkPermissionAndCenterLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            centerOnUserLocation();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void centerOnUserLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;

        try {
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (location != null) {
                double uLat = location.getLatitude();
                double uLng = location.getLongitude();
                binding.webViewMap.evaluateJavascript("setMapCenter(" + uLat + ", " + uLng + ", 17);", null);
                Toast.makeText(this, "Centered to current GPS location", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Acquiring GPS fix... Tap on map if needed.", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException getting location", e);
        }
    }

    private void confirmPickerLocation() {
        Intent resultData = new Intent();
        resultData.putExtra(EXTRA_LATITUDE, selectedLat);
        resultData.putExtra(EXTRA_LONGITUDE, selectedLng);

        // If an existing shopId was passed, update Firestore directly
        if (shopId != null && !shopId.isEmpty()) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("latitude", selectedLat);
            updates.put("longitude", selectedLng);

            FirebaseFirestore.getInstance().collection("shops").document(shopId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Shop location updated successfully!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK, resultData);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK, resultData);
                        finish();
                    });
        } else {
            setResult(RESULT_OK, resultData);
            finish();
        }
    }

    private void openDirectionsInGoogleMaps() {
        // Universal Google Maps navigation intent
        String uriStr = "https://www.google.com/maps/dir/?api=1&destination=" + selectedLat + "," + selectedLng;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriStr));
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public class MapBridgeInterface {
        @JavascriptInterface
        public void onLocationSelected(double lat, double lng) {
            runOnUiThread(() -> {
                selectedLat = lat;
                selectedLng = lng;
                if (binding != null) {
                    binding.tvCoordinates.setText(String.format(Locale.getDefault(), "Coordinates: %.5f, %.5f", lat, lng));
                }
            });
        }
    }
}

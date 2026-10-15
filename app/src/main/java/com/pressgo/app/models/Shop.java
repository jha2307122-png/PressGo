package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Shop {

    @DocumentId
    private String id;
    private String name;
    private String address;
    private String phone;
    private String email;
    private String description;
    private double latitude;
    private double longitude;
    private String openingHours;
    private String closingHours;
    private boolean isOpen;
    private List<String> supportedFileTypes;
    private List<String> availablePaperSizes;
    private boolean hasColorPrinting;
    private boolean hasDuplexPrinting;
    private boolean hasBinding;
    private boolean hasStapling;
    private boolean hasLamination;
    private double rating;
    private int totalRatings;
    private int estimatedQueueMinutes;
    private String imageUrl;
    private String ownerId;
    private boolean isActive;
    private Timestamp createdAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public Shop() {}

    public Shop(String id,
                String name,
                String address,
                String phone,
                String email,
                String description,
                double latitude,
                double longitude,
                String openingHours,
                String closingHours,
                boolean isOpen,
                List<String> supportedFileTypes,
                List<String> availablePaperSizes,
                boolean hasColorPrinting,
                boolean hasDuplexPrinting,
                boolean hasBinding,
                boolean hasStapling,
                boolean hasLamination,
                double rating,
                int totalRatings,
                int estimatedQueueMinutes,
                String imageUrl,
                String ownerId,
                boolean isActive,
                Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.openingHours = openingHours;
        this.closingHours = closingHours;
        this.isOpen = isOpen;
        this.supportedFileTypes = supportedFileTypes;
        this.availablePaperSizes = availablePaperSizes;
        this.hasColorPrinting = hasColorPrinting;
        this.hasDuplexPrinting = hasDuplexPrinting;
        this.hasBinding = hasBinding;
        this.hasStapling = hasStapling;
        this.hasLamination = hasLamination;
        this.rating = rating;
        this.totalRatings = totalRatings;
        this.estimatedQueueMinutes = estimatedQueueMinutes;
        this.imageUrl = imageUrl;
        this.ownerId = ownerId;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public String getName() { return name; }

    public String getAddress() { return address; }

    public String getPhone() { return phone; }

    public String getEmail() { return email; }

    public String getDescription() { return description; }

    public double getLatitude() { return latitude; }

    public double getLongitude() { return longitude; }

    public String getOpeningHours() { return openingHours; }

    public String getClosingHours() { return closingHours; }

    @com.google.firebase.firestore.PropertyName("isOpen")
    public boolean isOpen() { return isOpen; }

    @com.google.firebase.firestore.PropertyName("open")
    public boolean getOpen() { return isOpen; }

    public List<String> getSupportedFileTypes() { return supportedFileTypes; }

    public List<String> getAvailablePaperSizes() { return availablePaperSizes; }

    public boolean isHasColorPrinting() { return hasColorPrinting; }

    public boolean isHasDuplexPrinting() { return hasDuplexPrinting; }

    public boolean isHasBinding() { return hasBinding; }

    public boolean isHasStapling() { return hasStapling; }

    public boolean isHasLamination() { return hasLamination; }

    public double getRating() { return rating; }

    public int getTotalRatings() { return totalRatings; }

    public int getEstimatedQueueMinutes() { return estimatedQueueMinutes; }

    public String getImageUrl() { return imageUrl; }

    public String getOwnerId() { return ownerId; }

    @com.google.firebase.firestore.PropertyName("isActive")
    public boolean isActive() { return isActive; }

    @com.google.firebase.firestore.PropertyName("active")
    public boolean getActive() { return isActive; }

    public Timestamp getCreatedAt() { return createdAt; }

    // --- Convenience / Alias Getters ---
    public String getShopId() { return id != null ? id : ""; }
    public void setShopId(String shopId) { this.id = shopId; }

    public String getHours() {
        if (openingHours != null && closingHours != null) {
            return openingHours + " - " + closingHours;
        } else if (openingHours != null) {
            return openingHours;
        }
        return "9:00 AM - 6:00 PM";
    }

    public boolean supportsColor() { return hasColorPrinting; }
    public boolean supportsDuplex() { return hasDuplexPrinting; }
    public boolean supportsBinding() { return hasBinding; }
    public boolean supportsStapling() { return hasStapling; }
    public boolean supportsLamination() { return hasLamination; }

    public int getQueueLength() { return estimatedQueueMinutes > 0 ? (estimatedQueueMinutes / 5) + 1 : 0; }
    public int getEstimatedWaitMinutes() { return estimatedQueueMinutes; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setName(String name) { this.name = name; }

    public void setAddress(String address) { this.address = address; }

    public void setPhone(String phone) { this.phone = phone; }

    public void setEmail(String email) { this.email = email; }

    public void setDescription(String description) { this.description = description; }

    public void setLatitude(double latitude) { this.latitude = latitude; }

    public void setLongitude(double longitude) { this.longitude = longitude; }

    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    public void setClosingHours(String closingHours) { this.closingHours = closingHours; }

    @com.google.firebase.firestore.PropertyName("isOpen")
    public void setIsOpen(boolean open) { this.isOpen = open; }

    @com.google.firebase.firestore.PropertyName("open")
    public void setOpen(boolean open) { this.isOpen = open; }

    public void setSupportedFileTypes(List<String> supportedFileTypes) { this.supportedFileTypes = supportedFileTypes; }

    public void setAvailablePaperSizes(List<String> availablePaperSizes) { this.availablePaperSizes = availablePaperSizes; }

    public void setHasColorPrinting(boolean hasColorPrinting) { this.hasColorPrinting = hasColorPrinting; }

    public void setHasDuplexPrinting(boolean hasDuplexPrinting) { this.hasDuplexPrinting = hasDuplexPrinting; }

    public void setHasBinding(boolean hasBinding) { this.hasBinding = hasBinding; }

    public void setHasStapling(boolean hasStapling) { this.hasStapling = hasStapling; }

    public void setHasLamination(boolean hasLamination) { this.hasLamination = hasLamination; }

    public void setRating(double rating) { this.rating = rating; }

    public void setTotalRatings(int totalRatings) { this.totalRatings = totalRatings; }

    public void setEstimatedQueueMinutes(int estimatedQueueMinutes) { this.estimatedQueueMinutes = estimatedQueueMinutes; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    @com.google.firebase.firestore.PropertyName("isActive")
    public void setIsActive(boolean active) { isActive = active; }

    @com.google.firebase.firestore.PropertyName("active")
    public void setActive(boolean active) { isActive = active; }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("address", address);
        map.put("phone", phone);
        map.put("email", email);
        map.put("description", description);
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("openingHours", openingHours);
        map.put("closingHours", closingHours);
        map.put("isOpen", isOpen);
        map.put("open", isOpen);
        map.put("supportedFileTypes", supportedFileTypes);
        map.put("availablePaperSizes", availablePaperSizes);
        map.put("hasColorPrinting", hasColorPrinting);
        map.put("hasDuplexPrinting", hasDuplexPrinting);
        map.put("hasBinding", hasBinding);
        map.put("hasStapling", hasStapling);
        map.put("hasLamination", hasLamination);
        map.put("rating", rating);
        map.put("totalRatings", totalRatings);
        map.put("estimatedQueueMinutes", estimatedQueueMinutes);
        map.put("imageUrl", imageUrl);
        map.put("ownerId", ownerId);
        map.put("isActive", isActive);
        map.put("active", isActive);
        map.put("createdAt", createdAt);
        return map;
    }
}

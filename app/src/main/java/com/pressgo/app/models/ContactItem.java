package com.pressgo.app.models;

import java.io.Serializable;

/**
 * Domain model representing a device contact retrieved via Android's
 * Contacts Content Provider (ContactsContract).
 * (Week 10: Content Providers & Contacts Access).
 */
public class ContactItem implements Serializable {

    private long id;
    private String name;
    private String phoneNumber;
    private String photoUri;

    public ContactItem() {}

    public ContactItem(long id, String name, String phoneNumber, String photoUri) {
        this.id = id;
        this.name = name != null ? name.trim() : "";
        this.phoneNumber = phoneNumber != null ? phoneNumber.trim() : "";
        this.photoUri = photoUri;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name != null && !name.isEmpty() ? name : "Unnamed Contact";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPhotoUri() {
        return photoUri;
    }

    public void setPhotoUri(String photoUri) {
        this.photoUri = photoUri;
    }

    public boolean hasPhoneNumber() {
        return phoneNumber != null && !phoneNumber.trim().isEmpty();
    }

    public String getInitials() {
        String n = getName();
        if (n.isEmpty()) return "?";
        String[] parts = n.split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        } else {
            return n.substring(0, Math.min(2, n.length())).toUpperCase();
        }
    }
}

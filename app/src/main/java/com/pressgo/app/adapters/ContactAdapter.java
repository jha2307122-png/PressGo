package com.pressgo.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pressgo.app.databinding.ItemContactBinding;
import com.pressgo.app.models.ContactItem;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying device contacts retrieved from
 * Android's Contacts Content Provider (ContactsContract).
 * (Week 10: Content Providers & Lists).
 */
public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

    public interface OnContactClickListener {
        void onContactSelected(ContactItem contact);
    }

    private final List<ContactItem> fullList = new ArrayList<>();
    private final List<ContactItem> displayedList = new ArrayList<>();
    private final OnContactClickListener listener;

    public ContactAdapter(List<ContactItem> contacts, OnContactClickListener listener) {
        this.listener = listener;
        setContacts(contacts);
    }

    public void setContacts(List<ContactItem> contacts) {
        this.fullList.clear();
        this.displayedList.clear();
        if (contacts != null) {
            this.fullList.addAll(contacts);
            this.displayedList.addAll(contacts);
        }
        notifyDataSetChanged();
    }

    public void filter(String query) {
        displayedList.clear();
        if (query == null || query.trim().isEmpty()) {
            displayedList.addAll(fullList);
        } else {
            String lower = query.toLowerCase().trim();
            for (ContactItem item : fullList) {
                if (item.getName().toLowerCase().contains(lower) ||
                    (item.getPhoneNumber() != null && item.getPhoneNumber().contains(lower))) {
                    displayedList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContactBinding binding = ItemContactBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ContactViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        holder.bind(displayedList.get(position));
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    public class ContactViewHolder extends RecyclerView.ViewHolder {

        private final ItemContactBinding binding;

        public ContactViewHolder(@NonNull ItemContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ContactItem contact) {
            binding.tvContactName.setText(contact.getName());
            if (contact.hasPhoneNumber()) {
                binding.tvContactPhone.setText(contact.getPhoneNumber());
            } else {
                binding.tvContactPhone.setText("No phone number available");
            }
            binding.tvContactInitials.setText(contact.getInitials());

            if (contact.getPhotoUri() != null && !contact.getPhotoUri().trim().isEmpty()) {
                binding.ivContactPhoto.setVisibility(android.view.View.VISIBLE);
                com.bumptech.glide.Glide.with(binding.getRoot().getContext())
                        .load(android.net.Uri.parse(contact.getPhotoUri()))
                        .placeholder(com.pressgo.app.R.drawable.ic_person)
                        .error(com.pressgo.app.R.drawable.ic_person)
                        .into(binding.ivContactPhoto);
            } else {
                binding.ivContactPhoto.setVisibility(android.view.View.GONE);
                binding.tvContactInitials.setVisibility(android.view.View.VISIBLE);
            }

            binding.btnSelectContact.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onContactSelected(contact);
                }
            });

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onContactSelected(contact);
                }
            });
        }
    }
}

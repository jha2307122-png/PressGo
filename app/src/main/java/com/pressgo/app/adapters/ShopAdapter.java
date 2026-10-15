package com.pressgo.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.pressgo.app.R;
import com.pressgo.app.models.Shop;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying a list of {@link Shop} items.
 * Supports real-time list filtering via {@link #filterList(List)}.
 */
public class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ShopViewHolder> {

    // -------------------------------------------------------------------------
    // Click listener interface
    // -------------------------------------------------------------------------

    /** Callback interface for shop item click events. */
    public interface OnShopClickListener {
        void onShopClick(Shop shop);
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final Context            context;
    private       List<Shop>         shopList;
    private final OnShopClickListener clickListener;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public ShopAdapter(@NonNull List<Shop> shopList,
                       @NonNull OnShopClickListener clickListener) {
        this.context       = null;
        this.shopList      = new ArrayList<>(shopList);
        this.clickListener = clickListener;
    }

    public ShopAdapter(@NonNull Context context,
                       @NonNull List<Shop> shopList,
                       @NonNull OnShopClickListener clickListener) {
        this.context       = context;
        this.shopList      = new ArrayList<>(shopList);
        this.clickListener = clickListener;
    }

    // -------------------------------------------------------------------------
    // RecyclerView.Adapter overrides
    // -------------------------------------------------------------------------

    @NonNull
    @Override
    public ShopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = context != null ? context : parent.getContext();
        View view = LayoutInflater.from(ctx)
                .inflate(R.layout.item_shop, parent, false);
        return new ShopViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShopViewHolder holder, int position) {
        Shop shop = shopList.get(position);
        holder.bind(shop);
    }

    @Override
    public int getItemCount() {
        return shopList.size();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Replaces the current list with a filtered subset. Call this from a
     * {@link android.widget.SearchView} or filter text watcher.
     */
    public void filterList(@NonNull List<Shop> filteredList) {
        shopList = new ArrayList<>(filteredList);
        notifyDataSetChanged();
    }

    /** Replaces the entire data set and refreshes the list. */
    public void updateList(@NonNull List<Shop> newList) {
        shopList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    class ShopViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView   cardView;
        private final ShapeableImageView imgShopLogo;
        private final TextView           tvShopName;
        private final TextView           tvShopAddress;
        private final TextView           tvStatusBadge;
        private final RatingBar          ratingBar;
        private final TextView           tvRatingCount;
        private final TextView           tvWaitTime;

        ShopViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView      = itemView.findViewById(R.id.cardViewShop);
            imgShopLogo   = itemView.findViewById(R.id.imgShopLogo);
            tvShopName    = itemView.findViewById(R.id.tvShopName);
            tvShopAddress = itemView.findViewById(R.id.tvShopAddress);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            ratingBar     = itemView.findViewById(R.id.ratingBar);
            tvRatingCount = itemView.findViewById(R.id.tvRatingCount);
            tvWaitTime    = itemView.findViewById(R.id.tvWaitTime);
        }

        void bind(@NonNull Shop shop) {
            tvShopName.setText(shop.getName());
            tvShopAddress.setText(shop.getAddress() != null ? shop.getAddress() : "");

            // --- Rating ---
            ratingBar.setRating((float) shop.getRating());
            tvRatingCount.setText(String.format("(%.1f · %d)", shop.getRating(), shop.getTotalRatings()));

            // --- Wait time ---
            int waitMins = shop.getEstimatedQueueMinutes();
            if (waitMins <= 0) {
                tvWaitTime.setText("No wait");
            } else {
                tvWaitTime.setText(waitMins + " min wait");
            }

            // --- Status badge ---
            Context ctx = context != null ? context : itemView.getContext();
            if (shop.isOpen()) {
                tvStatusBadge.setText("Open");
                tvStatusBadge.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.status_ready)));
            } else {
                tvStatusBadge.setText("Closed");
                tvStatusBadge.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.status_rejected)));
            }

            // --- Shop logo ---
            if (shop.getImageUrl() != null && !shop.getImageUrl().isEmpty()) {
                Glide.with(ctx)
                        .load(shop.getImageUrl())
                        .placeholder(R.drawable.ic_shop)
                        .error(R.drawable.ic_shop)
                        .centerCrop()
                        .into(imgShopLogo);
            } else {
                imgShopLogo.setImageResource(R.drawable.ic_shop);
            }

            // --- Click ---
            cardView.setOnClickListener(v -> clickListener.onShopClick(shop));
        }
    }
}

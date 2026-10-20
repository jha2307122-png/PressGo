package com.pressgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.pressgo.app.R;
import com.pressgo.app.models.InventoryItem;

import java.util.List;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(InventoryItem item);
        void onItemLongClick(InventoryItem item);
    }

    private final List<InventoryItem> items;
    private final OnItemClickListener listener;

    public InventoryAdapter(List<InventoryItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = items.get(position);
        holder.tvItemName.setText(item.getItemName());
        holder.tvCategory.setText(item.getCategory());
        holder.tvQuantity.setText(item.getQuantity() + " " + (item.getUnit() != null ? item.getUnit() : "units"));
        holder.tvThreshold.setText("Threshold: " + item.getLowStockThreshold() + " " + (item.getUnit() != null ? item.getUnit() : ""));

        boolean isLowStock = item.isLowStock() || (item.getQuantity() <= item.getLowStockThreshold());
        if (isLowStock) {
            holder.ivWarning.setVisibility(View.VISIBLE);
            holder.tvQuantity.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.status_rejected));
        } else {
            holder.ivWarning.setVisibility(View.GONE);
            holder.tvQuantity.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(item);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvItemName, tvCategory, tvQuantity, tvThreshold;
        ImageView ivWarning;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvItemName = itemView.findViewById(R.id.tvItemName);
            tvCategory = itemView.findViewById(R.id.tvItemCategory);
            tvQuantity = itemView.findViewById(R.id.tvItemQuantity);
            tvThreshold = itemView.findViewById(R.id.tvItemThreshold);
            ivWarning = itemView.findViewById(R.id.ivLowStockWarning);
        }
    }
}

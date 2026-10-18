package com.pressgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.pressgo.app.R;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecentOrderAdapter extends RecyclerView.Adapter<RecentOrderAdapter.ViewHolder> {

    public interface OnRecentOrderClickListener {
        void onRecentOrderClick(PrintOrder order);
    }

    private final List<PrintOrder> orders;
    private final OnRecentOrderClickListener listener;

    public RecentOrderAdapter(List<PrintOrder> orders, OnRecentOrderClickListener listener) {
        this.orders = orders;
        this.listener = listener;
    }

    public void updateOrders(List<PrintOrder> newOrders) {
        orders.clear();
        if (newOrders != null) {
            orders.addAll(newOrders);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PrintOrder order = orders.get(position);

        String displayId = order.getId() != null && !order.getId().isEmpty() ? order.getId() : order.getOrderId();
        if (displayId != null && displayId.length() > 14) {
            displayId = "#" + displayId.substring(0, 10).toUpperCase();
        } else if (displayId != null && !displayId.startsWith("#") && !displayId.startsWith("PG-")) {
            displayId = "#" + displayId.toUpperCase();
        }
        holder.tvOrderId.setText(displayId != null ? displayId : "#ORDER");

        holder.tvShopName.setText(order.getShopName() != null && !order.getShopName().isEmpty()
                ? order.getShopName() : "Campus Printing Center");

        Timestamp ts = order.getSubmittedAt() != null ? order.getSubmittedAt() : order.getCreatedAt();
        holder.tvDate.setText(ts != null ? DateUtils.getTimeAgo(ts) : "Just now");

        double price = order.getFinalPrice() > 0 ? order.getFinalPrice() : order.getEstimatedPrice();
        holder.tvPrice.setText(String.format(Locale.US, "BDT %.2f", price));

        String status = order.getOrderStatus();
        if (status == null || status.trim().isEmpty()) {
            status = order.getStatus();
        }
        if (status == null) status = PrintOrder.STATUS_PENDING;

        int colorRes;
        String statusLabel;
        if (PrintOrder.STATUS_ACCEPTED.equalsIgnoreCase(status)) {
            statusLabel = "ACCEPTED";
            colorRes = R.color.status_accepted;
        } else if (PrintOrder.STATUS_PRINTING.equalsIgnoreCase(status)) {
            statusLabel = "PRINTING";
            colorRes = R.color.status_printing;
        } else if (PrintOrder.STATUS_READY_FOR_PICKUP.equalsIgnoreCase(status)) {
            boolean isPaid = "PAID".equalsIgnoreCase(order.getPaymentStatus());
            statusLabel = isPaid ? "READY • PICKUP" : "READY • PAY";
            colorRes = R.color.status_ready;
        } else if (PrintOrder.STATUS_COMPLETED.equalsIgnoreCase(status)) {
            statusLabel = "COMPLETED";
            colorRes = R.color.status_completed;
        } else if (PrintOrder.STATUS_REJECTED.equalsIgnoreCase(status)) {
            statusLabel = "REJECTED";
            colorRes = R.color.status_rejected;
        } else if (PrintOrder.STATUS_CANCELLED.equalsIgnoreCase(status)) {
            statusLabel = "CANCELLED";
            colorRes = R.color.status_cancelled;
        } else {
            statusLabel = "IN QUEUE";
            colorRes = R.color.status_pending;
        }

        holder.tvStatus.setText(statusLabel);
        holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), colorRes));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRecentOrderClick(order);
        });
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvShopName, tvDate, tvPrice, tvStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvShopName = itemView.findViewById(R.id.tvShopName);
            tvDate = itemView.findViewById(R.id.tvOrderDate);
            tvPrice = itemView.findViewById(R.id.tvOrderPrice);
            tvStatus = itemView.findViewById(R.id.tvOrderStatus);
        }
    }
}

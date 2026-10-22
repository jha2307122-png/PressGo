package com.pressgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pressgo.app.R;
import com.pressgo.app.models.Notification;
import com.pressgo.app.utils.DateUtils;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
    }

    private final List<Notification> notifications;
    private final OnNotificationClickListener clickListener;

    public NotificationAdapter(List<Notification> notifications, OnNotificationClickListener clickListener) {
        this.notifications = notifications;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notif = notifications.get(position);
        holder.tvTitle.setText(notif.getTitle());
        holder.tvMessage.setText(notif.getMessage());
        holder.tvTime.setText(DateUtils.getTimeAgo(notif.getCreatedAt()));

        if (notif.isRead()) {
            holder.unreadIndicator.setVisibility(View.GONE);
            holder.itemView.setAlpha(0.7f);
        } else {
            holder.unreadIndicator.setVisibility(View.VISIBLE);
            holder.itemView.setAlpha(1.0f);
        }

        if (Notification.TYPE_INVENTORY_ALERT.equals(notif.getType())) {
            holder.ivIcon.setImageResource(R.drawable.ic_notification);
        } else if (Notification.TYPE_READY_FOR_PICKUP.equals(notif.getType())) {
            holder.ivIcon.setImageResource(R.drawable.ic_qr_code);
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_order);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onNotificationClick(notif);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle;
        TextView tvMessage;
        TextView tvTime;
        View unreadIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivNotifIcon);
            tvTitle = itemView.findViewById(R.id.tvNotifTitle);
            tvMessage = itemView.findViewById(R.id.tvNotifMessage);
            tvTime = itemView.findViewById(R.id.tvNotifTime);
            unreadIndicator = itemView.findViewById(R.id.viewUnreadIndicator);
        }
    }
}

package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.pressgo.app.R;
import com.pressgo.app.adapters.NotificationAdapter;
import com.pressgo.app.databinding.ActivityNotificationsBinding;
import com.pressgo.app.models.Notification;
import com.pressgo.app.repositories.NotificationRepository;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.NotificationUtils;
import com.pressgo.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {
    private static final String TAG = "NotificationsActivity";

    private ActivityNotificationsBinding binding;
    private final List<Notification> notifications = new ArrayList<>();
    private NotificationAdapter adapter;
    private ListenerRegistration notificationsListener;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Notifications");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        NotificationUtils.createNotificationChannels(this);
        NotificationUtils.requestNotificationPermission(this);

        setupRecyclerView();
        loadNotifications();
        attachNotificationsListener();
    }

    private String resolveCurrentUserId() {
        if (UserRepository.getInstance().getCurrentUser() != null) {
            return UserRepository.getInstance().getCurrentUser().getUid();
        }
        String uid = sessionManager.getUserId();
        if (uid != null && !uid.trim().isEmpty()) {
            return uid;
        }
        return "demo_cust_uid";
    }

    private void setupRecyclerView() {
        adapter = new NotificationAdapter(notifications, notif -> {
            if (!notif.isRead()) {
                NotificationRepository.getInstance().markAsRead(notif.getId(), task -> {});
                notif.setRead(true);
                adapter.notifyDataSetChanged();
            }

            if (notif.getOrderId() != null && !notif.getOrderId().trim().isEmpty()) {
                Intent intent = new Intent(this, OrderTrackingActivity.class);
                intent.putExtra(Constants.EXTRA_ORDER_ID, notif.getOrderId().trim());
                startActivity(intent);
            }
        });
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(adapter);

        binding.swipeRefreshLayout.setOnRefreshListener(this::loadNotifications);
    }

    private void loadNotifications() {
        String uid = resolveCurrentUserId();

        binding.swipeRefreshLayout.setRefreshing(true);
        NotificationRepository.getInstance().getUserNotifications(uid, list -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            notifications.clear();
            if (list != null) {
                notifications.addAll(list);
            }
            adapter.notifyDataSetChanged();
            updateEmptyState();
        }, e -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            Log.e(TAG, "Failed to load notifications", e);
            updateEmptyState();
        });
    }

    private void attachNotificationsListener() {
        String uid = resolveCurrentUserId();
        if (notificationsListener != null) {
            notificationsListener.remove();
        }

        notificationsListener = NotificationRepository.getInstance().addNotificationsListener(uid, (snapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "Live notifications listener error", e);
                return;
            }
            if (snapshot == null) return;

            List<Notification> liveList = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Notification notif = doc.toObject(Notification.class);
                if (notif != null) {
                    notif.setId(doc.getId());
                    liveList.add(notif);
                }
            }

            Collections.sort(liveList, (n1, n2) -> {
                Timestamp t1 = n1.getCreatedAt();
                Timestamp t2 = n2.getCreatedAt();
                if (t1 == null && t2 == null) return 0;
                if (t1 == null) return 1;
                if (t2 == null) return -1;
                return t2.compareTo(t1);
            });

            notifications.clear();
            notifications.addAll(liveList);
            adapter.notifyDataSetChanged();
            updateEmptyState();
        });
    }

    private void updateEmptyState() {
        if (notifications.isEmpty()) {
            binding.emptyStateLayout.setVisibility(View.VISIBLE);
            binding.rvNotifications.setVisibility(View.GONE);
        } else {
            binding.emptyStateLayout.setVisibility(View.GONE);
            binding.rvNotifications.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notifications, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_mark_all_read) {
            String uid = resolveCurrentUserId();
            NotificationRepository.getInstance().markAllAsRead(uid, task -> {
                for (Notification n : notifications) n.setRead(true);
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "All notifications marked as read", Toast.LENGTH_SHORT).show();
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (notificationsListener != null) {
            notificationsListener.remove();
        }
    }
}

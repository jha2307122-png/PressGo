package com.pressgo.app.notifications;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.pressgo.app.models.Notification;
import com.pressgo.app.repositories.NotificationRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.NotificationUtils;

public class PressGoMessagingService extends FirebaseMessagingService {
    private static final String TAG = "PressGoFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Token: " + token);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore.getInstance()
                    .collection(Constants.COLLECTION_USERS)
                    .document(uid)
                    .update("fcmToken", token)
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update FCM token", e));
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        String title = "PressGo Update";
        String body = "You have a new update regarding your print order.";
        String orderId = null;
        String type = Notification.TYPE_GENERAL;

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        if (!remoteMessage.getData().isEmpty()) {
            if (remoteMessage.getData().containsKey("title")) {
                title = remoteMessage.getData().get("title");
            }
            if (remoteMessage.getData().containsKey("body")) {
                body = remoteMessage.getData().get("body");
            }
            if (remoteMessage.getData().containsKey("orderId")) {
                orderId = remoteMessage.getData().get("orderId");
            }
            if (remoteMessage.getData().containsKey("type")) {
                type = remoteMessage.getData().get("type");
            }
        }

        // Show local push notification
        NotificationUtils.createOrderNotification(this, title, body, orderId);

        // Store notification record in Firestore if user logged in
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Notification notif = new Notification();
            notif.setUserId(FirebaseAuth.getInstance().getCurrentUser().getUid());
            notif.setTitle(title);
            notif.setMessage(body);
            notif.setOrderId(orderId);
            notif.setType(type);
            notif.setRead(false);
            NotificationRepository.getInstance().createNotification(notif, task -> {});
        }
    }
}

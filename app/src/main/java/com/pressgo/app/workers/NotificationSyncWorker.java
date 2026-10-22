package com.pressgo.app.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.pressgo.app.models.Notification;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.NotificationUtils;

public class NotificationSyncWorker extends Worker {
    private static final String TAG = "NotificationSyncWorker";

    public NotificationSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return Result.success();
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        try {
            QuerySnapshot snapshots = Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection(Constants.COLLECTION_NOTIFICATIONS)
                            .whereEqualTo("userId", userId)
                            .whereEqualTo("read", false)
                            .get()
            );

            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                Notification notif = doc.toObject(Notification.class);
                if (notif != null) {
                    NotificationUtils.createOrderNotification(
                            getApplicationContext(),
                            notif.getTitle(),
                            notif.getMessage(),
                            notif.getOrderId()
                    );
                }
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error syncing notifications", e);
            return Result.retry();
        }
    }
}

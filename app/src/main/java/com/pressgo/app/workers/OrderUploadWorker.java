package com.pressgo.app.workers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.pressgo.app.models.OrderFile;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.DocumentUtils;

import java.io.InputStream;
import java.util.UUID;

public class OrderUploadWorker extends Worker {
    private static final String TAG = "OrderUploadWorker";
    public static final String KEY_ORDER_ID = "order_id";
    public static final String KEY_FILE_URI = "file_uri";
    public static final String KEY_FILE_NAME = "file_name";

    public OrderUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String orderId = getInputData().getString(KEY_ORDER_ID);
        String uriStr = getInputData().getString(KEY_FILE_URI);
        String fileName = getInputData().getString(KEY_FILE_NAME);

        if (orderId == null || uriStr == null) {
            return Result.failure();
        }

        try {
            Uri uri = Uri.parse(uriStr);
            InputStream stream = getApplicationContext().getContentResolver().openInputStream(uri);
            if (stream == null) {
                return Result.failure();
            }

            String storagePath = "orders/" + orderId + "/files/" + UUID.randomUUID().toString() + "_" + fileName;
            StorageReference ref = FirebaseStorage.getInstance().getReference().child(storagePath);

            UploadTask.TaskSnapshot snapshot = Tasks.await(ref.putStream(stream));
            Uri downloadUri = Tasks.await(ref.getDownloadUrl());

            OrderFile orderFile = new OrderFile();
            orderFile.setId(UUID.randomUUID().toString());
            orderFile.setOrderId(orderId);
            orderFile.setFileName(fileName);
            orderFile.setStoragePath(storagePath);
            orderFile.setDownloadUrl(downloadUri.toString());
            orderFile.setUploaded(true);
            orderFile.setUploadedAt(Timestamp.now());
            orderFile.setFileSizeBytes(DocumentUtils.getFileSize(getApplicationContext(), uri));

            Tasks.await(FirebaseFirestore.getInstance()
                    .collection(Constants.COLLECTION_ORDERS)
                    .document(orderId)
                    .collection(Constants.COLLECTION_ORDER_FILES)
                    .document(orderFile.getId())
                    .set(orderFile));

            Data output = new Data.Builder()
                    .putString("download_url", downloadUri.toString())
                    .build();

            return Result.success(output);
        } catch (Exception e) {
            Log.e(TAG, "Error in OrderUploadWorker", e);
            if (getRunAttemptCount() < 3) {
                return Result.retry();
            }
            return Result.failure();
        }
    }
}

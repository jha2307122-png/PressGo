package com.pressgo.app.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Generic HTTP Networking Client using OkHttp.
 * Demonstrates HTTP REST operations, JSON payload transmission, file streaming,
 * and asynchronous networking on Android.
 * (Week 11: HTTP/Networking Libraries).
 */
public class HttpNetworkManager {

    private static final String TAG = "HttpNetworkManager";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static HttpNetworkManager instance;
    private final OkHttpClient client;
    private final Handler mainHandler;

    public interface NetworkCallback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    private HttpNetworkManager() {
        mainHandler = new Handler(Looper.getMainLooper());

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Log.d(TAG, message));
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();
    }

    public static synchronized HttpNetworkManager getInstance() {
        if (instance == null) {
            instance = new HttpNetworkManager();
        }
        return instance;
    }

    public OkHttpClient getClient() {
        return client;
    }

    /**
     * Executes an asynchronous HTTP GET request.
     */
    public void get(String url, Map<String, String> headers, NetworkCallback<String> callback) {
        Request.Builder builder = new Request.Builder().url(url);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        Request request = builder.build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        IOException ex = new IOException("HTTP " + response.code() + ": " + response.message());
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError(ex);
                        });
                        return;
                    }
                    String responseString = body.string();
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(responseString);
                    });
                }
            }
        });
    }

    /**
     * Executes an asynchronous HTTP POST request with a JSON body.
     */
    public void postJson(String url, String jsonBody, NetworkCallback<String> callback) {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        IOException ex = new IOException("HTTP " + response.code() + ": " + response.message());
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError(ex);
                        });
                        return;
                    }
                    String responseString = responseBody.string();
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(responseString);
                    });
                }
            }
        });
    }

    /**
     * Downloads a remote file over HTTP using OkHttp streaming.
     */
    public void downloadFile(String fileUrl, File destinationFile, NetworkCallback<File> callback) {
        Request request = new Request.Builder().url(fileUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    IOException ex = new IOException("Failed to download file: " + response.code());
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError(ex);
                    });
                    return;
                }

                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(destinationFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.flush();
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(destinationFile);
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError(e);
                    });
                }
            }
        });
    }
}

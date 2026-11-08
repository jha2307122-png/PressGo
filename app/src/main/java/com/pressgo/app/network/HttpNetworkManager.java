package com.pressgo.app.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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
 * Generic HTTP Networking & JSON Parsing Client using OkHttp and Gson.
 * Supports asynchronous REST operations (GET, POST), JSON deserialization into domain models,
 * custom headers, HTTP error code handling, malformed JSON detection, connection timeouts,
 * file streaming, and thread-safe callbacks to the Android main thread.
 * (Week 11: HTTP Networking & JSON Parsing).
 */
public class HttpNetworkManager {

    private static final String TAG = "HttpNetworkManager";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static HttpNetworkManager instance;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private final Gson gson;

    public interface NetworkCallback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    /**
     * Dedicated exception representing an unsuccessful HTTP response status code (non-2xx).
     */
    public static class HttpException extends IOException {
        private final int statusCode;
        private final String responseBody;

        public HttpException(int statusCode, String message, String responseBody) {
            super("HTTP " + statusCode + (message != null && !message.isEmpty() ? ": " + message : ""));
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }
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

        gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .serializeNulls()
                .create();
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

    public Gson getGson() {
        return gson;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON Serialization / Deserialization Utilities
    // ─────────────────────────────────────────────────────────────────────────

    public <T> T fromJson(String json, Class<T> clazz) throws JsonSyntaxException {
        if (json == null) return null;
        return gson.fromJson(json, clazz);
    }

    public <T> T fromJson(String json, Type type) throws JsonSyntaxException {
        if (json == null) return null;
        return gson.fromJson(json, type);
    }

    public String toJson(Object obj) {
        if (obj == null) return "null";
        return gson.toJson(obj);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Raw HTTP GET & POST
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes an asynchronous HTTP GET request returning the raw String response.
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
                postError(callback, translateNetworkError(e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        String errorText = body != null ? body.string() : "";
                        postError(callback, new HttpException(response.code(), response.message(), errorText));
                        return;
                    }
                    String responseString = body.string();
                    postSuccess(callback, responseString);
                } catch (Exception ex) {
                    postError(callback, ex);
                }
            }
        });
    }

    /**
     * Executes an asynchronous HTTP POST request with a raw JSON body string.
     */
    public void postJson(String url, String jsonBody, NetworkCallback<String> callback) {
        RequestBody body = RequestBody.create(jsonBody != null ? jsonBody : "", JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postError(callback, translateNetworkError(e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        String errorText = responseBody != null ? responseBody.string() : "";
                        postError(callback, new HttpException(response.code(), response.message(), errorText));
                        return;
                    }
                    String responseString = responseBody.string();
                    postSuccess(callback, responseString);
                } catch (Exception ex) {
                    postError(callback, ex);
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Typed JSON REST Methods (Using Gson Deserialization)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes an asynchronous HTTP GET request and deserializes the JSON response
     * into the specified model class using Gson.
     */
    public <T> void getJson(String url, Map<String, String> headers, Class<T> responseClass, NetworkCallback<T> callback) {
        get(url, headers, new NetworkCallback<String>() {
            @Override
            public void onSuccess(String rawJson) {
                try {
                    T parsed = gson.fromJson(rawJson, responseClass);
                    postSuccess(callback, parsed);
                } catch (JsonSyntaxException jse) {
                    Log.e(TAG, "Malformed JSON syntax from GET " + url, jse);
                    postError(callback, new JsonSyntaxException("Malformed JSON received: " + jse.getMessage(), jse));
                } catch (JsonParseException jpe) {
                    Log.e(TAG, "JSON parsing error from GET " + url, jpe);
                    postError(callback, new JsonParseException("Failed to parse JSON response: " + jpe.getMessage(), jpe));
                } catch (Exception e) {
                    postError(callback, e);
                }
            }

            @Override
            public void onError(Exception error) {
                postError(callback, error);
            }
        });
    }

    /**
     * Executes an asynchronous HTTP GET request and deserializes the JSON response
     * into the specified generic Type (e.g., List<MyModel>) using Gson.
     */
    public <T> void getJson(String url, Map<String, String> headers, Type typeToken, NetworkCallback<T> callback) {
        get(url, headers, new NetworkCallback<String>() {
            @Override
            public void onSuccess(String rawJson) {
                try {
                    T parsed = gson.fromJson(rawJson, typeToken);
                    postSuccess(callback, parsed);
                } catch (JsonSyntaxException jse) {
                    Log.e(TAG, "Malformed JSON syntax from GET " + url, jse);
                    postError(callback, new JsonSyntaxException("Malformed JSON received: " + jse.getMessage(), jse));
                } catch (JsonParseException jpe) {
                    Log.e(TAG, "JSON parsing error from GET " + url, jpe);
                    postError(callback, new JsonParseException("Failed to parse JSON response: " + jpe.getMessage(), jpe));
                } catch (Exception e) {
                    postError(callback, e);
                }
            }

            @Override
            public void onError(Exception error) {
                postError(callback, error);
            }
        });
    }

    /**
     * Serializes requestObject to JSON, executes an HTTP POST request,
     * and deserializes the JSON response into responseClass using Gson.
     */
    public <T> void postJson(String url, Object requestObject, Class<T> responseClass, NetworkCallback<T> callback) {
        String jsonPayload;
        try {
            jsonPayload = requestObject instanceof String ? (String) requestObject : gson.toJson(requestObject);
        } catch (Exception e) {
            postError(callback, new IllegalArgumentException("Failed to serialize request body to JSON: " + e.getMessage(), e));
            return;
        }

        postJson(url, jsonPayload, new NetworkCallback<String>() {
            @Override
            public void onSuccess(String rawJson) {
                try {
                    T parsed = gson.fromJson(rawJson, responseClass);
                    postSuccess(callback, parsed);
                } catch (JsonSyntaxException jse) {
                    Log.e(TAG, "Malformed JSON syntax from POST " + url, jse);
                    postError(callback, new JsonSyntaxException("Malformed JSON received: " + jse.getMessage(), jse));
                } catch (JsonParseException jpe) {
                    Log.e(TAG, "JSON parsing error from POST " + url, jpe);
                    postError(callback, new JsonParseException("Failed to parse JSON response: " + jpe.getMessage(), jpe));
                } catch (Exception e) {
                    postError(callback, e);
                }
            }

            @Override
            public void onError(Exception error) {
                postError(callback, error);
            }
        });
    }

    /**
     * Serializes requestObject to JSON, executes an HTTP POST request,
     * and deserializes the JSON response into generic Type using Gson.
     */
    public <T> void postJson(String url, Object requestObject, Type typeToken, NetworkCallback<T> callback) {
        String jsonPayload;
        try {
            jsonPayload = requestObject instanceof String ? (String) requestObject : gson.toJson(requestObject);
        } catch (Exception e) {
            postError(callback, new IllegalArgumentException("Failed to serialize request body to JSON: " + e.getMessage(), e));
            return;
        }

        postJson(url, jsonPayload, new NetworkCallback<String>() {
            @Override
            public void onSuccess(String rawJson) {
                try {
                    T parsed = gson.fromJson(rawJson, typeToken);
                    postSuccess(callback, parsed);
                } catch (JsonSyntaxException jse) {
                    Log.e(TAG, "Malformed JSON syntax from POST " + url, jse);
                    postError(callback, new JsonSyntaxException("Malformed JSON received: " + jse.getMessage(), jse));
                } catch (JsonParseException jpe) {
                    Log.e(TAG, "JSON parsing error from POST " + url, jpe);
                    postError(callback, new JsonParseException("Failed to parse JSON response: " + jpe.getMessage(), jpe));
                } catch (Exception e) {
                    postError(callback, e);
                }
            }

            @Override
            public void onError(Exception error) {
                postError(callback, error);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File Streaming Download
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Downloads a remote file over HTTP using OkHttp streaming.
     */
    public void downloadFile(String fileUrl, File destinationFile, NetworkCallback<File> callback) {
        Request request = new Request.Builder().url(fileUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postError(callback, translateNetworkError(e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    String errorText = response.body() != null ? response.body().string() : "";
                    postError(callback, new HttpException(response.code(), "Failed to download file", errorText));
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
                    postSuccess(callback, destinationFile);
                } catch (Exception e) {
                    postError(callback, e);
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error & Threading Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private IOException translateNetworkError(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return new IOException("Network request timed out: " + e.getMessage(), e);
        } else if (e instanceof UnknownHostException) {
            return new IOException("Unable to reach server. Please check your internet connection.", e);
        }
        return e;
    }

    private <T> void postSuccess(NetworkCallback<T> callback, T result) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(NetworkCallback<T> callback, Exception error) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onError(error));
    }
}

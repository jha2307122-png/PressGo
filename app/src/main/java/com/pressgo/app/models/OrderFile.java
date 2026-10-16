package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import java.util.HashMap;
import java.util.Map;

public class OrderFile {

    @DocumentId
    private String id;
    private String orderId;
    private String fileName;
    private String fileType;
    private long fileSizeBytes;
    private int pageCount;
    private String storagePath;
    private String downloadUrl;
    private String contentUri;
    private String localPath;
    private String base64Data;
    private Timestamp uploadedAt;
    private boolean isUploaded;

    /** Local-only URI string – not persisted to Firestore. */
    @Exclude
    private transient String localUri;

    /** Upload progress 0-100 – not persisted to Firestore. */
    @Exclude
    private transient int uploadProgress;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public OrderFile() {}

    public OrderFile(String id,
                     String orderId,
                     String fileName,
                     String fileType,
                     long fileSizeBytes,
                     int pageCount,
                     String storagePath,
                     String downloadUrl,
                     String contentUri,
                     Timestamp uploadedAt,
                     boolean isUploaded) {
        this.id = id;
        this.orderId = orderId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSizeBytes = fileSizeBytes;
        this.pageCount = pageCount;
        this.storagePath = storagePath;
        this.downloadUrl = downloadUrl;
        this.contentUri = contentUri;
        this.uploadedAt = uploadedAt;
        this.isUploaded = isUploaded;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public String getOrderId() { return orderId; }

    public String getFileName() { return fileName; }

    public String getFileType() { return fileType; }

    public long getFileSizeBytes() { return fileSizeBytes; }

    public int getPageCount() { return pageCount; }

    @Exclude
    public int getColorPageCount() { return 0; }

    public String getStoragePath() { return storagePath; }

    public String getDownloadUrl() { return downloadUrl; }

    public String getContentUri() { return contentUri; }

    public String getLocalPath() { return localPath; }

    public String getBase64Data() { return base64Data; }

    public Timestamp getUploadedAt() { return uploadedAt; }

    public boolean isUploaded() { return isUploaded; }

    @Exclude
    public String getLocalUri() { return localPath != null ? localPath : localUri; }

    @Exclude
    public int getUploadProgress() { return uploadProgress; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setOrderId(String orderId) { this.orderId = orderId; }

    public void setFileName(String fileName) { this.fileName = fileName; }

    public void setFileType(String fileType) { this.fileType = fileType; }

    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public void setContentUri(String contentUri) { this.contentUri = contentUri; }

    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public void setBase64Data(String base64Data) { this.base64Data = base64Data; }

    public void setUploadedAt(Timestamp uploadedAt) { this.uploadedAt = uploadedAt; }

    public void setUploaded(boolean uploaded) { isUploaded = uploaded; }

    @Exclude
    public void setLocalUri(String localUri) { this.localUri = localUri; }

    @Exclude
    public void setUploadProgress(int uploadProgress) { this.uploadProgress = uploadProgress; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("orderId", orderId);
        map.put("fileName", fileName);
        map.put("fileType", fileType);
        map.put("fileSizeBytes", fileSizeBytes);
        map.put("pageCount", pageCount);
        map.put("storagePath", storagePath);
        map.put("downloadUrl", downloadUrl);
        map.put("contentUri", contentUri);
        map.put("localPath", localPath);
        map.put("base64Data", base64Data);
        map.put("uploadedAt", uploadedAt);
        map.put("isUploaded", isUploaded);
        return map;
    }
}

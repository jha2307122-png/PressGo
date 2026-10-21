package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

public class CompressionRecord {

    // -------------------------------------------------------------------------
    // Compression level constants
    // -------------------------------------------------------------------------
    public static final String LEVEL_LOW    = "LOW";
    public static final String LEVEL_MEDIUM = "MEDIUM";
    public static final String LEVEL_HIGH   = "HIGH";

    // -------------------------------------------------------------------------
    // Status constants
    // -------------------------------------------------------------------------
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_SUCCESS      = "SUCCESS";
    public static final String STATUS_FAILED       = "FAILED";

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    @DocumentId
    private String id;
    private String userId;
    private String inputFileName;
    private String outputFileName;
    private String fileType;
    private long inputSizeBytes;
    private long outputSizeBytes;
    private String compressionLevel;
    private long savedBytes;
    private double percentReduction;
    private String status;
    private String localOutputPath;
    private Timestamp createdAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public CompressionRecord() {}

    public CompressionRecord(String id,
                             String userId,
                             String inputFileName,
                             String outputFileName,
                             String fileType,
                             long inputSizeBytes,
                             long outputSizeBytes,
                             String compressionLevel,
                             long savedBytes,
                             double percentReduction,
                             String status,
                             String localOutputPath,
                             Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.inputFileName = inputFileName;
        this.outputFileName = outputFileName;
        this.fileType = fileType;
        this.inputSizeBytes = inputSizeBytes;
        this.outputSizeBytes = outputSizeBytes;
        this.compressionLevel = compressionLevel;
        this.savedBytes = savedBytes;
        this.percentReduction = percentReduction;
        this.status = status;
        this.localOutputPath = localOutputPath;
        this.createdAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public String getUserId() { return userId; }

    public String getInputFileName() { return inputFileName; }

    public String getOutputFileName() { return outputFileName; }

    public String getFileType() { return fileType; }

    public long getInputSizeBytes() { return inputSizeBytes; }

    public long getOutputSizeBytes() { return outputSizeBytes; }

    public String getCompressionLevel() { return compressionLevel; }

    public long getSavedBytes() { return savedBytes; }

    public double getPercentReduction() { return percentReduction; }

    public String getStatus() { return status; }

    public String getLocalOutputPath() { return localOutputPath; }

    public Timestamp getCreatedAt() { return createdAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setUserId(String userId) { this.userId = userId; }

    public void setInputFileName(String inputFileName) { this.inputFileName = inputFileName; }

    public void setOutputFileName(String outputFileName) { this.outputFileName = outputFileName; }

    public void setFileType(String fileType) { this.fileType = fileType; }

    public void setInputSizeBytes(long inputSizeBytes) { this.inputSizeBytes = inputSizeBytes; }

    public void setOutputSizeBytes(long outputSizeBytes) { this.outputSizeBytes = outputSizeBytes; }

    public void setCompressionLevel(String compressionLevel) { this.compressionLevel = compressionLevel; }

    public void setSavedBytes(long savedBytes) { this.savedBytes = savedBytes; }

    public void setPercentReduction(double percentReduction) { this.percentReduction = percentReduction; }

    public void setStatus(String status) { this.status = status; }

    public void setLocalOutputPath(String localOutputPath) { this.localOutputPath = localOutputPath; }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("inputFileName", inputFileName);
        map.put("outputFileName", outputFileName);
        map.put("fileType", fileType);
        map.put("inputSizeBytes", inputSizeBytes);
        map.put("outputSizeBytes", outputSizeBytes);
        map.put("compressionLevel", compressionLevel);
        map.put("savedBytes", savedBytes);
        map.put("percentReduction", percentReduction);
        map.put("status", status);
        map.put("localOutputPath", localOutputPath);
        map.put("createdAt", createdAt);
        return map;
    }
}

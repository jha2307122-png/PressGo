package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

public class ConversionRecord {

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
    private String inputFileType;
    private String outputFileName;
    private String outputFileType;
    private long inputSizeBytes;
    private long outputSizeBytes;
    private String status;
    private String errorMessage;
    private Timestamp createdAt;
    private String localOutputPath;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public ConversionRecord() {}

    public ConversionRecord(String id,
                            String userId,
                            String inputFileName,
                            String inputFileType,
                            String outputFileName,
                            String outputFileType,
                            long inputSizeBytes,
                            long outputSizeBytes,
                            String status,
                            String errorMessage,
                            Timestamp createdAt,
                            String localOutputPath) {
        this.id = id;
        this.userId = userId;
        this.inputFileName = inputFileName;
        this.inputFileType = inputFileType;
        this.outputFileName = outputFileName;
        this.outputFileType = outputFileType;
        this.inputSizeBytes = inputSizeBytes;
        this.outputSizeBytes = outputSizeBytes;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.localOutputPath = localOutputPath;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public String getUserId() { return userId; }

    public String getInputFileName() { return inputFileName; }

    public String getInputFileType() { return inputFileType; }

    public String getOutputFileName() { return outputFileName; }

    public String getOutputFileType() { return outputFileType; }

    public long getInputSizeBytes() { return inputSizeBytes; }

    public long getOutputSizeBytes() { return outputSizeBytes; }

    public String getStatus() { return status; }

    public String getErrorMessage() { return errorMessage; }

    public Timestamp getCreatedAt() { return createdAt; }

    public String getLocalOutputPath() { return localOutputPath; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setUserId(String userId) { this.userId = userId; }

    public void setInputFileName(String inputFileName) { this.inputFileName = inputFileName; }

    public void setInputFileType(String inputFileType) { this.inputFileType = inputFileType; }

    public void setOutputFileName(String outputFileName) { this.outputFileName = outputFileName; }

    public void setOutputFileType(String outputFileType) { this.outputFileType = outputFileType; }

    public void setInputSizeBytes(long inputSizeBytes) { this.inputSizeBytes = inputSizeBytes; }

    public void setOutputSizeBytes(long outputSizeBytes) { this.outputSizeBytes = outputSizeBytes; }

    public void setStatus(String status) { this.status = status; }

    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public void setLocalOutputPath(String localOutputPath) { this.localOutputPath = localOutputPath; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("inputFileName", inputFileName);
        map.put("inputFileType", inputFileType);
        map.put("outputFileName", outputFileName);
        map.put("outputFileType", outputFileType);
        map.put("inputSizeBytes", inputSizeBytes);
        map.put("outputSizeBytes", outputSizeBytes);
        map.put("status", status);
        map.put("errorMessage", errorMessage);
        map.put("createdAt", createdAt);
        map.put("localOutputPath", localOutputPath);
        return map;
    }
}

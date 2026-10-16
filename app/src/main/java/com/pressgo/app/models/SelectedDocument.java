package com.pressgo.app.models;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Local-only model for the document selection UI.
 * Implements Serializable and Parcelable so it can safely be passed between activities.
 */
public class SelectedDocument implements Serializable, Parcelable {

    private String uriString;
    private transient Uri uri;
    private String fileName;
    private String fileType;
    private long fileSizeBytes;
    private int pageCount;
    private boolean isPageCountKnown;
    private int uploadProgress;
    private boolean isUploaded;
    private String errorMessage;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public SelectedDocument() {}

    public SelectedDocument(Uri uri, String fileName, String fileType, long fileSizeBytes) {
        this(uri, fileName, fileType, fileSizeBytes, 0, false, 0, false, null);
    }

    public SelectedDocument(Uri uri,
                            String fileName,
                            String fileType,
                            long fileSizeBytes,
                            int pageCount,
                            boolean isPageCountKnown,
                            int uploadProgress,
                            boolean isUploaded,
                            String errorMessage) {
        this.uri = uri;
        this.uriString = (uri != null) ? uri.toString() : null;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSizeBytes = fileSizeBytes;
        this.pageCount = pageCount;
        this.isPageCountKnown = isPageCountKnown;
        this.uploadProgress = uploadProgress;
        this.isUploaded = isUploaded;
        this.errorMessage = errorMessage;
    }

    protected SelectedDocument(Parcel in) {
        uriString = in.readString();
        fileName = in.readString();
        fileType = in.readString();
        fileSizeBytes = in.readLong();
        pageCount = in.readInt();
        isPageCountKnown = in.readByte() != 0;
        uploadProgress = in.readInt();
        isUploaded = in.readByte() != 0;
        errorMessage = in.readString();
        if (uriString != null) {
            try {
                uri = Uri.parse(uriString);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uri != null ? uri.toString() : uriString);
        dest.writeString(fileName);
        dest.writeString(fileType);
        dest.writeLong(fileSizeBytes);
        dest.writeInt(pageCount);
        dest.writeByte((byte) (isPageCountKnown ? 1 : 0));
        dest.writeInt(uploadProgress);
        dest.writeByte((byte) (isUploaded ? 1 : 0));
        dest.writeString(errorMessage);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SelectedDocument> CREATOR = new Creator<SelectedDocument>() {
        @Override
        public SelectedDocument createFromParcel(Parcel in) {
            return new SelectedDocument(in);
        }

        @Override
        public SelectedDocument[] newArray(int size) {
            return new SelectedDocument[size];
        }
    };

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Uri getUri() {
        if (uri == null && uriString != null) {
            try {
                uri = Uri.parse(uriString);
            } catch (Exception ignored) {}
        }
        return uri;
    }

    public String getFileName() { return fileName; }

    public String getFileType() { return fileType; }

    public long getFileSizeBytes() { return fileSizeBytes; }

    public int getPageCount() { return pageCount; }

    public boolean isPageCountKnown() { return isPageCountKnown; }

    public int getUploadProgress() { return uploadProgress; }

    public boolean isUploaded() { return isUploaded; }

    public String getErrorMessage() { return errorMessage; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setUri(Uri uri) {
        this.uri = uri;
        this.uriString = (uri != null) ? uri.toString() : null;
    }

    public void setFileName(String fileName) { this.fileName = fileName; }

    public void setFileType(String fileType) { this.fileType = fileType; }

    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public void setPageCountKnown(boolean pageCountKnown) { isPageCountKnown = pageCountKnown; }

    public void setUploadProgress(int uploadProgress) { this.uploadProgress = uploadProgress; }

    public void setUploaded(boolean uploaded) { isUploaded = uploaded; }

    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}

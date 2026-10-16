package com.pressgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pressgo.app.R;
import com.pressgo.app.models.SelectedDocument;
import com.pressgo.app.utils.DocumentUtils;

import java.util.List;

public class SelectedDocumentAdapter extends RecyclerView.Adapter<SelectedDocumentAdapter.ViewHolder> {

    public interface OnRemoveClickListener {
        void onRemoveClick(int position, SelectedDocument document);
    }

    private final List<SelectedDocument> documents;
    private final OnRemoveClickListener removeListener;

    public SelectedDocumentAdapter(List<SelectedDocument> documents, OnRemoveClickListener removeListener) {
        this.documents = documents;
        this.removeListener = removeListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selected_document, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SelectedDocument doc = documents.get(position);
        holder.tvFileName.setText(doc.getFileName());

        String details = DocumentUtils.formatFileSize(doc.getFileSizeBytes());
        if (doc.isPageCountKnown() && doc.getPageCount() > 0) {
            details += " • " + doc.getPageCount() + " page(s)";
        } else {
            details += " • Page count unknown";
        }
        holder.tvFileDetails.setText(details);

        // Icon based on type
        String ext = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        if (ext.contains("pdf")) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_pdf);
        } else if (ext.contains("doc")) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_word);
        } else if (ext.contains("ppt")) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_pptx);
        } else if (ext.contains("jpg") || ext.contains("png") || ext.contains("jpeg")) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_image);
        } else {
            holder.ivFileIcon.setImageResource(R.drawable.ic_print);
        }

        if (doc.getErrorMessage() != null && !doc.getErrorMessage().isEmpty()) {
            holder.tvError.setVisibility(View.VISIBLE);
            holder.tvError.setText(doc.getErrorMessage());
        } else {
            holder.tvError.setVisibility(View.GONE);
        }

        if (doc.getUploadProgress() > 0 && doc.getUploadProgress() < 100) {
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.progressBar.setProgress(doc.getUploadProgress());
        } else {
            holder.progressBar.setVisibility(View.GONE);
        }

        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && removeListener != null) {
                removeListener.onRemoveClick(pos, doc);
            }
        });
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFileIcon;
        TextView tvFileName;
        TextView tvFileDetails;
        TextView tvError;
        ProgressBar progressBar;
        ImageButton btnRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.ivFileIcon);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileDetails = itemView.findViewById(R.id.tvFileDetails);
            tvError = itemView.findViewById(R.id.tvError);
            progressBar = itemView.findViewById(R.id.progressBarUpload);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}

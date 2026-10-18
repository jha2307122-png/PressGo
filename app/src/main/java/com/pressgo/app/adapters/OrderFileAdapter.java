package com.pressgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.pressgo.app.R;
import com.pressgo.app.models.OrderFile;
import com.pressgo.app.utils.DocumentUtils;

import java.util.List;

public class OrderFileAdapter extends RecyclerView.Adapter<OrderFileAdapter.ViewHolder> {

    public interface OnFileActionListener {
        void onDownload(OrderFile file);
        void onOpen(OrderFile file);
    }

    private final List<OrderFile> files;
    private final OnFileActionListener listener;

    public OrderFileAdapter(List<OrderFile> files, OnFileActionListener listener) {
        this.files = files;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OrderFile file = files.get(position);
        holder.tvFileName.setText(file.getFileName() != null ? file.getFileName() : "Document");

        StringBuilder details = new StringBuilder();
        if (file.getFileSizeBytes() > 0) {
            details.append(DocumentUtils.formatFileSize(file.getFileSizeBytes()));
        }
        if (file.getPageCount() > 0) {
            if (details.length() > 0) details.append(" • ");
            details.append(file.getPageCount()).append(" page(s)");
        }
        if (file.getFileType() != null && !file.getFileType().isEmpty()) {
            if (details.length() > 0) details.append(" • ");
            details.append(file.getFileType().toUpperCase());
        }
        holder.tvFileDetails.setText(details.length() > 0 ? details.toString() : "Attached Document");

        // Icon based on filename / type
        String ext = DocumentUtils.getFileExtension(file.getFileName());
        if (ext.isEmpty() && file.getFileType() != null) {
            ext = file.getFileType().toLowerCase();
        }

        if ("pdf".equalsIgnoreCase(ext)) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_pdf);
        } else if ("doc".equalsIgnoreCase(ext) || "docx".equalsIgnoreCase(ext)) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_word);
        } else if ("ppt".equalsIgnoreCase(ext) || "pptx".equalsIgnoreCase(ext)) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_pptx);
        } else if ("jpg".equalsIgnoreCase(ext) || "jpeg".equalsIgnoreCase(ext) || "png".equalsIgnoreCase(ext)) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_image);
        } else {
            holder.ivFileIcon.setImageResource(R.drawable.ic_print);
        }

        holder.btnDownloadFile.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDownload(file);
            }
        });

        holder.btnOpenFile.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOpen(file);
            }
        });
    }

    @Override
    public int getItemCount() {
        return files != null ? files.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFileIcon;
        TextView tvFileName;
        TextView tvFileDetails;
        MaterialButton btnDownloadFile;
        MaterialButton btnOpenFile;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.ivFileIcon);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileDetails = itemView.findViewById(R.id.tvFileDetails);
            btnDownloadFile = itemView.findViewById(R.id.btnDownloadFile);
            btnOpenFile = itemView.findViewById(R.id.btnOpenFile);
        }
    }
}

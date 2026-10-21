package com.pressgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pressgo.app.R;
import com.pressgo.app.models.CompressionRecord;
import com.pressgo.app.utils.DateUtils;
import com.pressgo.app.utils.DocumentUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class CompressionHistoryAdapter extends RecyclerView.Adapter<CompressionHistoryAdapter.ViewHolder> {

    public interface OnCompressionClickListener {
        void onCompressionClick(CompressionRecord record);
    }

    public interface OnCompressionDownloadListener {
        void onDownloadClick(CompressionRecord record);
    }

    private final List<CompressionRecord> records;
    private final OnCompressionClickListener listener;
    private final OnCompressionDownloadListener downloadListener;

    public CompressionHistoryAdapter(List<CompressionRecord> records, OnCompressionClickListener listener, OnCompressionDownloadListener downloadListener) {
        this.records = records;
        this.listener = listener;
        this.downloadListener = downloadListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_compression, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CompressionRecord rec = records.get(position);
        holder.tvInputName.setText(rec.getInputFileName());
        holder.tvOutputName.setText(rec.getOutputFileName() != null ? "Output: " + rec.getOutputFileName() : "Compressed file");
        holder.tvDate.setText(DateUtils.getTimeAgo(rec.getCreatedAt()));

        String sizeText = DocumentUtils.formatFileSize(rec.getInputSizeBytes());
        if (rec.getOutputSizeBytes() > 0) {
            sizeText += " -> " + DocumentUtils.formatFileSize(rec.getOutputSizeBytes());
        }
        holder.tvSize.setText(sizeText);

        if (rec.getPercentReduction() > 0) {
            holder.tvReductionBadge.setVisibility(View.VISIBLE);
            holder.tvReductionBadge.setText(String.format(Locale.US, "-%.1f%%", rec.getPercentReduction()));
        } else {
            holder.tvReductionBadge.setVisibility(View.GONE);
        }

        boolean hasFile = rec.getLocalOutputPath() != null && new File(rec.getLocalOutputPath()).exists();
        holder.btnDownload.setVisibility(hasFile ? View.VISIBLE : View.GONE);
        holder.btnDownload.setOnClickListener(v -> {
            if (downloadListener != null) {
                downloadListener.onDownloadClick(rec);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCompressionClick(rec);
        });
    }

    @Override
    public int getItemCount() {
        return records != null ? records.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvInputName, tvOutputName, tvSize, tvDate, tvReductionBadge;
        ImageView ivStatusIcon;
        View btnDownload;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInputName = itemView.findViewById(R.id.tvInputName);
            tvOutputName = itemView.findViewById(R.id.tvOutputName);
            tvSize = itemView.findViewById(R.id.tvSize);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvReductionBadge = itemView.findViewById(R.id.tvReductionBadge);
            ivStatusIcon = itemView.findViewById(R.id.ivStatusIcon);
            btnDownload = itemView.findViewById(R.id.btnDownload);
        }
    }
}

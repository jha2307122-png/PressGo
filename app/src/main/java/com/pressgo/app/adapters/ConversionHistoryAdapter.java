package com.pressgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.pressgo.app.R;
import com.pressgo.app.models.ConversionRecord;
import com.pressgo.app.utils.DateUtils;
import com.pressgo.app.utils.DocumentUtils;

import java.io.File;
import java.util.List;

public class ConversionHistoryAdapter extends RecyclerView.Adapter<ConversionHistoryAdapter.ViewHolder> {

    public interface OnConversionClickListener {
        void onConversionClick(ConversionRecord record);
    }

    public interface OnConversionDownloadListener {
        void onDownloadClick(ConversionRecord record);
    }

    private final List<ConversionRecord> records;
    private final OnConversionClickListener listener;
    private OnConversionDownloadListener downloadListener;

    public ConversionHistoryAdapter(List<ConversionRecord> records, OnConversionClickListener listener) {
        this.records = records;
        this.listener = listener;
    }

    public ConversionHistoryAdapter(List<ConversionRecord> records, OnConversionClickListener listener, OnConversionDownloadListener downloadListener) {
        this.records = records;
        this.listener = listener;
        this.downloadListener = downloadListener;
    }

    public void setDownloadListener(OnConversionDownloadListener downloadListener) {
        this.downloadListener = downloadListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConversionRecord rec = records.get(position);
        holder.tvInputName.setText(rec.getInputFileName());
        holder.tvOutputName.setText(rec.getOutputFileName() != null ? rec.getOutputFileName() : "N/A");
        holder.tvDate.setText(DateUtils.getTimeAgo(rec.getCreatedAt()));

        String sizeText = DocumentUtils.formatFileSize(rec.getInputSizeBytes());
        if (rec.getOutputSizeBytes() > 0) {
            sizeText += " -> " + DocumentUtils.formatFileSize(rec.getOutputSizeBytes());
        }
        holder.tvSize.setText(sizeText);

        holder.tvStatus.setText(rec.getStatus());
        if (ConversionRecord.STATUS_SUCCESS.equals(rec.getStatus())) {
            holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.status_ready));
            holder.ivStatusIcon.setImageResource(R.drawable.ic_convert);
        } else if (ConversionRecord.STATUS_FAILED.equals(rec.getStatus())) {
            holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.status_rejected));
            holder.ivStatusIcon.setImageResource(R.drawable.empty_state);
        } else {
            holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.status_accepted));
            holder.ivStatusIcon.setImageResource(R.drawable.ic_convert);
        }

        boolean hasFile = rec.getLocalOutputPath() != null && new File(rec.getLocalOutputPath()).exists();
        holder.btnDownload.setVisibility(hasFile ? View.VISIBLE : View.GONE);
        holder.btnDownload.setOnClickListener(v -> {
            if (downloadListener != null) {
                downloadListener.onDownloadClick(rec);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onConversionClick(rec);
        });
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvInputName, tvOutputName, tvSize, tvDate, tvStatus;
        ImageView ivStatusIcon;
        View btnDownload;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInputName = itemView.findViewById(R.id.tvInputName);
            tvOutputName = itemView.findViewById(R.id.tvOutputName);
            tvSize = itemView.findViewById(R.id.tvSize);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            ivStatusIcon = itemView.findViewById(R.id.ivStatusIcon);
            btnDownload = itemView.findViewById(R.id.btnDownload);
        }
    }
}

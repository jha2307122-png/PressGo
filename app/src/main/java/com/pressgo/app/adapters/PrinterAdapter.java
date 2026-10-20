package com.pressgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.pressgo.app.R;
import com.pressgo.app.models.Printer;

import java.util.ArrayList;
import java.util.List;

public class PrinterAdapter extends RecyclerView.Adapter<PrinterAdapter.ViewHolder> {

    public interface OnPrinterClickListener {
        void onPrinterClick(Printer printer);
    }

    private final List<Printer> printers;
    private final OnPrinterClickListener listener;

    public PrinterAdapter() {
        this.printers = new ArrayList<>();
        this.listener = null;
    }

    public PrinterAdapter(List<Printer> printers, OnPrinterClickListener listener) {
        this.printers = printers != null ? printers : new ArrayList<>();
        this.listener = listener;
    }

    public void submitList(List<Printer> newPrinters) {
        this.printers.clear();
        if (newPrinters != null) {
            this.printers.addAll(newPrinters);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_printer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Printer printer = printers.get(position);
        holder.tvPrinterName.setText(printer.getName());
        holder.tvPrinterModel.setText(printer.getModel() != null ? printer.getModel() : "Standard Printer");

        holder.tvStatus.setText(printer.getStatus());
        int statusColor;
        if (Printer.STATUS_AVAILABLE.equals(printer.getStatus())) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_ready);
        } else if (Printer.STATUS_BUSY.equals(printer.getStatus())) {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_pending);
        } else {
            statusColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_rejected);
        }
        holder.tvStatus.setTextColor(statusColor);

        holder.pbPaper.setProgress(printer.getPaperLevel());
        holder.tvPaperLevel.setText(printer.getPaperLevel() + "%");

        holder.pbInk.setProgress(printer.getInkLevel());
        holder.tvInkLevel.setText(printer.getInkLevel() + "%");

        holder.pbToner.setProgress(printer.getTonerLevel());
        holder.tvTonerLevel.setText(printer.getTonerLevel() + "%");

        if (printer.getCurrentJobId() != null && !printer.getCurrentJobId().isEmpty()) {
            holder.tvCurrentJob.setVisibility(View.VISIBLE);
            holder.tvCurrentJob.setText("Current Job: " + printer.getCurrentJobId());
        } else {
            holder.tvCurrentJob.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPrinterClick(printer);
        });
    }

    @Override
    public int getItemCount() {
        return printers.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPrinterName, tvPrinterModel, tvStatus, tvCurrentJob;
        ProgressBar pbPaper, pbInk, pbToner;
        TextView tvPaperLevel, tvInkLevel, tvTonerLevel;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPrinterName = itemView.findViewById(R.id.tvPrinterName);
            tvPrinterModel = itemView.findViewById(R.id.tvPrinterModel);
            tvStatus = itemView.findViewById(R.id.tvPrinterStatus);
            tvCurrentJob = itemView.findViewById(R.id.tvCurrentJob);
            pbPaper = itemView.findViewById(R.id.pbPaper);
            pbInk = itemView.findViewById(R.id.pbInk);
            pbToner = itemView.findViewById(R.id.pbToner);
            tvPaperLevel = itemView.findViewById(R.id.tvPaperLevel);
            tvInkLevel = itemView.findViewById(R.id.tvInkLevel);
            tvTonerLevel = itemView.findViewById(R.id.tvTonerLevel);
        }
    }
}

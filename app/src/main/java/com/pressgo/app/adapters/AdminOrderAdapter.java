package com.pressgo.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.pressgo.app.R;
import com.pressgo.app.models.OrderFile;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.utils.FileDownloadUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RecyclerView adapter for the admin order-management screen.
 * Shows order ID, customer name, status badge, file/page count, priority badge,
 * submission time, and contextual quick-action button.
 */
public class AdminOrderAdapter extends RecyclerView.Adapter<AdminOrderAdapter.AdminOrderViewHolder> {

    // -------------------------------------------------------------------------
    // Click listener interfaces
    // -------------------------------------------------------------------------

    public interface OnAdminOrderClickListener {
        void onAdminOrderClick(PrintOrder order);
    }

    public interface OnOrderClickListener {
        void onOrderClick(PrintOrder order);
    }

    public interface OnOrderLongClickListener {
        void onOrderLongClick(PrintOrder order);
    }

    public interface OnQuickActionListener {
        void onQuickAction(PrintOrder order, String action);
    }

    // Quick-action constants forwarded to the listener
    public static final String ACTION_ACCEPT      = "ACCEPT";
    public static final String ACTION_START_PRINT = "START_PRINT";
    public static final String ACTION_MARK_READY  = "MARK_READY";

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final Context                   context;
    private       List<PrintOrder>          orderList;
    private final OnAdminOrderClickListener adminClickListener;
    private final OnOrderClickListener      orderClickListener;
    private final OnOrderLongClickListener  longClickListener;
    private       OnQuickActionListener     quickActionListener;
    private final Set<String>               selectedOrderIds = new HashSet<>();

    public void setQuickActionListener(OnQuickActionListener quickActionListener) {
        this.quickActionListener = quickActionListener;
    }

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public AdminOrderAdapter(@NonNull Context context,
                             @NonNull List<PrintOrder> orderList,
                             @NonNull OnAdminOrderClickListener clickListener,
                             @NonNull OnQuickActionListener quickActionListener) {
        this.context             = context;
        this.orderList           = orderList != null ? orderList : new ArrayList<>();
        this.adminClickListener  = clickListener;
        this.orderClickListener  = clickListener::onAdminOrderClick;
        this.longClickListener   = null;
        this.quickActionListener = quickActionListener;
    }

    public AdminOrderAdapter(@NonNull List<PrintOrder> orderList,
                             @NonNull OnOrderClickListener clickListener,
                             @NonNull OnOrderLongClickListener longClickListener) {
        this.context             = null;
        this.orderList           = orderList != null ? orderList : new ArrayList<>();
        this.adminClickListener  = clickListener::onOrderClick;
        this.orderClickListener  = clickListener;
        this.longClickListener   = longClickListener;
        this.quickActionListener = null;
    }

    public AdminOrderAdapter(@NonNull List<PrintOrder> orderList,
                             @NonNull OnAdminOrderClickListener clickListener) {
        this.context             = null;
        this.orderList           = orderList != null ? orderList : new ArrayList<>();
        this.adminClickListener  = clickListener;
        this.orderClickListener  = clickListener::onAdminOrderClick;
        this.longClickListener   = null;
        this.quickActionListener = null;
    }

    // -------------------------------------------------------------------------
    // Selection API
    // -------------------------------------------------------------------------

    public void selectOrder(String orderId) {
        if (orderId != null) {
            selectedOrderIds.add(orderId);
            notifyDataSetChanged();
        }
    }

    public void deselectOrder(String orderId) {
        if (orderId != null) {
            selectedOrderIds.remove(orderId);
            notifyDataSetChanged();
        }
    }

    public void clearSelection() {
        selectedOrderIds.clear();
        notifyDataSetChanged();
    }

    // -------------------------------------------------------------------------
    // RecyclerView.Adapter overrides
    // -------------------------------------------------------------------------

    @NonNull
    @Override
    public AdminOrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = context != null ? context : parent.getContext();
        View view = LayoutInflater.from(ctx)
                .inflate(R.layout.item_admin_order, parent, false);
        return new AdminOrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AdminOrderViewHolder holder, int position) {
        holder.bind(orderList.get(position));
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void updateList(@NonNull List<PrintOrder> newList) {
        if (orderList != newList) {
            orderList.clear();
            orderList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    // -------------------------------------------------------------------------
    // Status helpers
    // -------------------------------------------------------------------------

    @ColorInt
    private int getStatusColor(Context ctx, @NonNull String status) {
        switch (status) {
            case PrintOrder.ORDER_STATUS_PENDING:          return ContextCompat.getColor(ctx, R.color.status_pending);
            case PrintOrder.ORDER_STATUS_ACCEPTED:         return ContextCompat.getColor(ctx, R.color.status_accepted);
            case PrintOrder.ORDER_STATUS_PRINTING:         return ContextCompat.getColor(ctx, R.color.status_printing);
            case PrintOrder.ORDER_STATUS_READY_FOR_PICKUP: return ContextCompat.getColor(ctx, R.color.status_ready);
            case PrintOrder.ORDER_STATUS_COMPLETED:        return ContextCompat.getColor(ctx, R.color.status_completed);
            case PrintOrder.ORDER_STATUS_REJECTED:         return ContextCompat.getColor(ctx, R.color.status_rejected);
            case PrintOrder.ORDER_STATUS_CANCELLED:        return ContextCompat.getColor(ctx, R.color.status_cancelled);
            default:                                       return ContextCompat.getColor(ctx, R.color.status_pending);
        }
    }

    private String getStatusText(@NonNull String status) {
        switch (status) {
            case PrintOrder.ORDER_STATUS_PENDING:          return "Pending";
            case PrintOrder.ORDER_STATUS_ACCEPTED:         return "Accepted";
            case PrintOrder.ORDER_STATUS_PRINTING:         return "Printing";
            case PrintOrder.ORDER_STATUS_READY_FOR_PICKUP: return "Ready for Pickup";
            case PrintOrder.ORDER_STATUS_COMPLETED:        return "Completed";
            case PrintOrder.ORDER_STATUS_REJECTED:         return "Rejected";
            case PrintOrder.ORDER_STATUS_CANCELLED:        return "Cancelled";
            default:                                       return "Unknown";
        }
    }

    @Nullable
    private String[] getQuickAction(@NonNull String status) {
        switch (status) {
            case PrintOrder.ORDER_STATUS_PENDING:  return new String[]{"Accept", ACTION_ACCEPT};
            case PrintOrder.ORDER_STATUS_ACCEPTED: return new String[]{"Start Print", ACTION_START_PRINT};
            case PrintOrder.ORDER_STATUS_PRINTING: return new String[]{"Mark Ready", ACTION_MARK_READY};
            default:                               return null;
        }
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    class AdminOrderViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardView;
        private final TextView         tvOrderId;
        private final TextView         tvCustomerName;
        private final TextView         tvStatusBadge;
        private final TextView         tvPageCount;
        private final TextView         tvSubmissionTime;
        private final ImageView        ivPriorityBadge;
        private final Button           btnDownloadDoc;
        private final Button           btnQuickAction;

        AdminOrderViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView         = itemView.findViewById(R.id.cardViewAdminOrder);
            tvOrderId        = itemView.findViewById(R.id.tvOrderId);
            tvCustomerName   = itemView.findViewById(R.id.tvCustomerName);
            tvStatusBadge    = itemView.findViewById(R.id.tvStatusBadge);
            tvPageCount      = itemView.findViewById(R.id.tvPageCount);
            tvSubmissionTime = itemView.findViewById(R.id.tvSubmissionTime);
            ivPriorityBadge  = itemView.findViewById(R.id.ivPriorityBadge);
            btnDownloadDoc   = itemView.findViewById(R.id.btnDownloadDoc);
            btnQuickAction   = itemView.findViewById(R.id.btnQuickAction);
        }

        void bind(@NonNull PrintOrder order) {
            Context ctx = context != null ? context : itemView.getContext();

            // Order ID
            String idStr = order.getId() != null ? order.getId() : order.getOrderId();
            String shortId = idStr != null && idStr.length() > 8
                    ? "#" + idStr.substring(0, 8).toUpperCase()
                    : "#" + (idStr != null ? idStr.toUpperCase() : "N/A");
            tvOrderId.setText(shortId);

            // Customer name
            tvCustomerName.setText(order.getCustomerName() != null ? order.getCustomerName() : "Unknown");

            // Status badge
            String status = order.getOrderStatus() != null
                    ? order.getOrderStatus() : (order.getStatus() != null ? order.getStatus() : PrintOrder.ORDER_STATUS_PENDING);
            tvStatusBadge.setText(getStatusText(status));
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(getStatusColor(ctx, status)));

            // Page / file count
            tvPageCount.setText(order.getTotalPages() + " pages");

            // Submission time
            com.google.firebase.Timestamp ts = order.getSubmittedAt() != null ? order.getSubmittedAt() : order.getCreatedAt();
            if (ts != null) {
                Date date = ts.toDate();
                tvSubmissionTime.setText(DATE_FORMAT.format(date));
            } else {
                tvSubmissionTime.setText("—");
            }

            // Priority badge (red star)
            ivPriorityBadge.setVisibility(order.isPriority() ? View.VISIBLE : View.GONE);

            // Download document button
            if (btnDownloadDoc != null) {
                btnDownloadDoc.setVisibility(View.VISIBLE);
                btnDownloadDoc.setOnClickListener(v -> {
                    String oid = order.getId() != null ? order.getId() : order.getOrderId();
                    Toast.makeText(ctx, "Fetching documents for " + shortId + "...", Toast.LENGTH_SHORT).show();
                    OrderRepository.getInstance().getOrderFiles(oid, files -> {
                        if (files != null && !files.isEmpty()) {
                            for (OrderFile f : files) {
                                FileDownloadUtils.downloadOrderFile(ctx, f, new FileDownloadUtils.OnDownloadListener() {
                                    @Override
                                    public void onSuccess(java.io.File localFile, Uri contentUri) {
                                        Toast.makeText(ctx, "Downloaded: " + f.getFileName(), Toast.LENGTH_SHORT).show();
                                        if (localFile != null && localFile.exists()) {
                                            FileDownloadUtils.openFile(ctx, localFile);
                                        } else if (contentUri != null) {
                                            FileDownloadUtils.openUri(ctx, contentUri, f.getFileName());
                                        }
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        Toast.makeText(ctx, "Download failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        } else {
                            Toast.makeText(ctx, "No attached files found for this order", Toast.LENGTH_SHORT).show();
                        }
                    }, e -> Toast.makeText(ctx, "Failed to load files: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                });
            }

            // Quick-action button
            String[] action = getQuickAction(status);
            if (action != null && quickActionListener != null) {
                btnQuickAction.setVisibility(View.VISIBLE);
                btnQuickAction.setText(action[0]);
                final String actionConstant = action[1];
                btnQuickAction.setOnClickListener(v -> quickActionListener.onQuickAction(order, actionConstant));
            } else {
                btnQuickAction.setVisibility(View.GONE);
                btnQuickAction.setOnClickListener(null);
            }

            // Selection highlight
            boolean isSelected = selectedOrderIds.contains(order.getId()) || selectedOrderIds.contains(order.getOrderId());
            cardView.setStrokeWidth(isSelected ? 6 : 1);
            cardView.setStrokeColor(isSelected ? ContextCompat.getColor(ctx, R.color.primary)
                    : ContextCompat.getColor(ctx, R.color.card_stroke));

            // Row click
            cardView.setOnClickListener(v -> {
                if (orderClickListener != null) {
                    orderClickListener.onOrderClick(order);
                } else if (adminClickListener != null) {
                    adminClickListener.onAdminOrderClick(order);
                }
            });

            // Long click
            if (longClickListener != null) {
                cardView.setOnLongClickListener(v -> {
                    longClickListener.onOrderLongClick(order);
                    return true;
                });
            }
        }
    }
}

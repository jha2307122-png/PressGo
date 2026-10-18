package com.pressgo.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.pressgo.app.R;
import com.pressgo.app.models.PrintOrder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the customer's order history list.
 * Displays order ID, shop name, colour-coded status badge, estimated price,
 * submission date, and file count.
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    // -------------------------------------------------------------------------
    // Click listener interface
    // -------------------------------------------------------------------------

    /** Callback interface for order item click events. */
    public interface OnOrderClickListener {
        void onOrderClick(PrintOrder order);
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final Context             context;
    private       List<PrintOrder>    orderList;
    private final OnOrderClickListener clickListener;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public OrderAdapter(@NonNull List<PrintOrder> orderList,
                        @NonNull OnOrderClickListener clickListener) {
        this.context       = null;
        this.orderList     = new ArrayList<>(orderList);
        this.clickListener = clickListener;
    }

    public OrderAdapter(@NonNull Context context,
                        @NonNull List<PrintOrder> orderList,
                        @NonNull OnOrderClickListener clickListener) {
        this.context       = context;
        this.orderList     = new ArrayList<>(orderList);
        this.clickListener = clickListener;
    }

    // -------------------------------------------------------------------------
    // RecyclerView.Adapter overrides
    // -------------------------------------------------------------------------

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = context != null ? context : parent.getContext();
        View view = LayoutInflater.from(ctx)
                .inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        holder.bind(orderList.get(position));
    }

    @Override
    public int getItemCount() {
        return orderList != null ? orderList.size() : 0;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Replaces the list content and notifies the adapter. */
    public void updateList(@NonNull List<PrintOrder> newList) {
        this.orderList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    // -------------------------------------------------------------------------
    // Status helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the colour resource ID that should be used for a given order status badge.
     *
     * @param status One of the {@link PrintOrder#ORDER_STATUS_*} constants.
     * @return A resolved colour integer.
     */
    @ColorInt
    public int getStatusColor(Context viewContext, @NonNull String status) {
        Context ctx = context != null ? context : viewContext;
        if (ctx == null) return 0xFF1A237E;
        switch (status) {
            case PrintOrder.ORDER_STATUS_ACCEPTED:
                return ContextCompat.getColor(ctx, R.color.status_accepted);
            case PrintOrder.ORDER_STATUS_PRINTING:
                return ContextCompat.getColor(ctx, R.color.status_printing);
            case PrintOrder.ORDER_STATUS_READY_FOR_PICKUP:
                return ContextCompat.getColor(ctx, R.color.status_ready);
            case PrintOrder.ORDER_STATUS_COMPLETED:
                return ContextCompat.getColor(ctx, R.color.status_completed);
            case PrintOrder.ORDER_STATUS_REJECTED:
                return ContextCompat.getColor(ctx, R.color.status_rejected);
            case PrintOrder.ORDER_STATUS_CANCELLED:
                return ContextCompat.getColor(ctx, R.color.status_cancelled);
            case PrintOrder.ORDER_STATUS_PENDING:
            default:
                return ContextCompat.getColor(ctx, R.color.status_pending);
        }
    }

    @ColorInt
    public int getStatusColor(@NonNull String status) {
        return getStatusColor(null, status);
    }

    /**
     * Returns a human-readable label for a given order status.
     *
     * @param status One of the {@link PrintOrder#ORDER_STATUS_*} constants.
     * @return Display string such as "Pending" or "Ready for Pickup".
     */
    public String getStatusText(@NonNull String status) {
        switch (status) {
            case PrintOrder.ORDER_STATUS_PENDING:          return "In Queue";
            case PrintOrder.ORDER_STATUS_ACCEPTED:         return "Accepted • In Queue";
            case PrintOrder.ORDER_STATUS_PRINTING:         return "Printing...";
            case PrintOrder.ORDER_STATUS_READY_FOR_PICKUP: return "Ready for Pickup";
            case PrintOrder.ORDER_STATUS_COMPLETED:        return "Completed";
            case PrintOrder.ORDER_STATUS_REJECTED:         return "Rejected";
            case PrintOrder.ORDER_STATUS_CANCELLED:        return "Cancelled";
            default:                                       return "In Queue";
        }
    }

    public String getStatusTextForOrder(@NonNull PrintOrder order) {
        String status = order.getOrderStatus();
        if (status == null || status.trim().isEmpty()) {
            status = order.getStatus();
        }
        if (status == null) status = PrintOrder.ORDER_STATUS_PENDING;

        if (PrintOrder.ORDER_STATUS_READY_FOR_PICKUP.equalsIgnoreCase(status)) {
            boolean isPaid = "PAID".equalsIgnoreCase(order.getPaymentStatus());
            return isPaid ? "Ready • Show QR" : "Ready • Pay Online";
        }
        return getStatusText(status);
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    class OrderViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardView;
        private final TextView         tvOrderId;
        private final TextView         tvShopName;
        private final TextView         tvStatusBadge;
        private final TextView         tvPrice;
        private final TextView         tvDate;
        private final TextView         tvFileCount;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView      = itemView.findViewById(R.id.cardViewOrder);
            tvOrderId     = itemView.findViewById(R.id.tvOrderId);
            tvShopName    = itemView.findViewById(R.id.tvShopName);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            tvPrice       = itemView.findViewById(R.id.tvPrice);
            tvDate        = itemView.findViewById(R.id.tvDate);
            tvFileCount   = itemView.findViewById(R.id.tvFileCount);
        }

        void bind(@NonNull PrintOrder order) {
            // Order ID (truncated to 8 chars for readability)
            String shortId = order.getId() != null && order.getId().length() > 8
                    ? "#" + order.getId().substring(0, 8).toUpperCase()
                    : "#" + (order.getId() != null ? order.getId().toUpperCase() : "N/A");
            tvOrderId.setText(shortId);

            tvShopName.setText(order.getShopName() != null ? order.getShopName() : "Unknown Shop");

            // Status badge
            String status = order.getOrderStatus();
            if (status == null || status.trim().isEmpty()) {
                status = order.getStatus();
            }
            if (status == null) status = PrintOrder.ORDER_STATUS_PENDING;
            tvStatusBadge.setText(getStatusTextForOrder(order));
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(getStatusColor(itemView.getContext(), status)));

            // Price
            double price = order.getFinalPrice() > 0 ? order.getFinalPrice() : order.getEstimatedPrice();
            tvPrice.setText(String.format(Locale.getDefault(), "BDT %.2f", price));

            // Submission date
            Timestamp ts = order.getSubmittedAt() != null ? order.getSubmittedAt() : order.getCreatedAt();
            if (ts != null) {
                Date date = ts.toDate();
                tvDate.setText(DATE_FORMAT.format(date));
            } else {
                tvDate.setText("—");
            }

            // Print configuration summary
            String config = (order.isColor() ? "Colour" : "B&W") + " • " +
                    (order.isBothSides() ? "Duplex" : "Single") +
                    (order.isHasBinding() ? " • Binding" : "") +
                    (order.getCustomerNotes() != null && !order.getCustomerNotes().trim().isEmpty() ? " • 📝 Note" : "") +
                    " (" + order.getTotalPages() + " pages)";
            tvFileCount.setText(config);

            // Click
            cardView.setOnClickListener(v -> clickListener.onOrderClick(order));
        }
    }
}

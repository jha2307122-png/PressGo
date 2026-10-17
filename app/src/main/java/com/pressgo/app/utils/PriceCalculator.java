package com.pressgo.app.utils;

import com.pressgo.app.models.OrderFile;
import com.pressgo.app.models.PriceBreakdown;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.models.ShopPricing;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for print price calculations and page-range parsing.
 */
public final class PriceCalculator {

    private PriceCalculator() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Price estimation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates an estimated price for the given order using shop pricing.
     *
     * @param order   The print order (must not be null)
     * @param pricing The shop's pricing configuration (must not be null)
     * @return A {@link PriceBreakdown} with per-page cost, subtotal, and total.
     */
    public static PriceBreakdown calculateEstimatedPrice(PrintOrder order,
                                                          ShopPricing pricing) {
        if (order == null || pricing == null) {
            return new PriceBreakdown();
        }

        int effectivePages = getEffectivePageCount(order);
        int copies         = Math.max(1, order.getCopies());

        boolean isColor = order.isColor() || order.getColorPages() > 0;
        int colorPages = isColor ? effectivePages : 0;
        int bwPages = isColor ? 0 : effectivePages;

        double bwRate = pricing.getBwPageRate();
        double colorRate = pricing.getColorPageRate();

        double bwCharge = bwPages * bwRate;
        double colorCharge = colorPages * colorRate;

        // Paper multiplier
        double paperMultiplier = 1.0;
        String paperSize = order.getPaperSize();
        if ("A3".equalsIgnoreCase(paperSize)) {
            paperMultiplier = pricing.getA3Multiplier() > 0 ? pricing.getA3Multiplier() : 1.5;
        } else if ("Letter".equalsIgnoreCase(paperSize)) {
            paperMultiplier = pricing.getLetterMultiplier() > 0 ? pricing.getLetterMultiplier() : 1.0;
        } else if ("A4".equalsIgnoreCase(paperSize)) {
            paperMultiplier = pricing.getA4Multiplier() > 0 ? pricing.getA4Multiplier() : 1.0;
        }

        // Duplex discount per page if applicable
        double duplexDiscount = 0.0;
        if (order.isBothSides() && pricing.getDuplexDiscount() > 0) {
            duplexDiscount = pricing.getDuplexDiscount() * effectivePages * copies;
        }

        double bindingCharge = (order.isHasBinding() || order.hasBinding()) ? pricing.getBindingCharge() : 0.0;
        double staplingCharge = (order.isHasStapling() || order.hasStapling()) ? pricing.getStaplingCharge() : 0.0;
        double priorityCharge = order.isPriority() ? pricing.getPriorityCharge() : 0.0;
        double discounts = 0.0;

        double subtotal = (bwCharge + colorCharge) * paperMultiplier * copies;
        double total = subtotal + bindingCharge + staplingCharge + priorityCharge - duplexDiscount - discounts;
        if (pricing.getMinimumOrder() > 0 && total < pricing.getMinimumOrder()) {
            total = pricing.getMinimumOrder();
        }
        if (total < 0) total = 0;

        return new PriceBreakdown(
                effectivePages,
                colorPages,
                bwPages,
                copies,
                bwCharge,
                colorCharge,
                paperMultiplier,
                duplexDiscount,
                bindingCharge,
                staplingCharge,
                priorityCharge,
                discounts,
                subtotal,
                total
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page range helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates whether a page-range string is syntactically correct and
     * falls within {@code [1, totalPages]}.
     *
     * <p>Valid formats: {@code "all"}, {@code "1"}, {@code "1-5"}, {@code "1,3,5-7"}.
     *
     * @param pageRange  User-supplied range string
     * @param totalPages Total number of pages in the document
     * @return {@code true} if valid
     */
    public static boolean validatePageRange(String pageRange, int totalPages) {
        if (pageRange == null || pageRange.trim().isEmpty()) return false;
        if ("all".equalsIgnoreCase(pageRange.trim())) return true;

        try {
            List<Integer> pages = parsePageRange(pageRange, totalPages);
            return !pages.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parses a page-range string into an ordered, de-duplicated list of
     * 1-based page numbers.
     *
     * <p>Accepts {@code "all"}, single numbers, ranges ({@code "2-5"}),
     * and comma-separated combinations ({@code "1,3-5,8"}).
     *
     * @param pageRange  Page range string
     * @param totalPages Total pages in document (used to resolve "all")
     * @return List of resolved page numbers in ascending order
     * @throws IllegalArgumentException if the range string is invalid
     */
    public static List<Integer> parsePageRange(String pageRange, int totalPages) {
        List<Integer> pages = new ArrayList<>();
        if (pageRange == null || pageRange.trim().isEmpty()) return pages;

        String trimmed = pageRange.trim();
        if ("all".equalsIgnoreCase(trimmed)) {
            for (int i = 1; i <= totalPages; i++) pages.add(i);
            return pages;
        }

        String[] parts = trimmed.split(",");
        for (String part : parts) {
            String token = part.trim();
            if (token.contains("-")) {
                String[] bounds = token.split("-", 2);
                int start = Integer.parseInt(bounds[0].trim());
                int end   = Integer.parseInt(bounds[1].trim());
                if (start < 1 || end > totalPages || start > end) {
                    throw new IllegalArgumentException(
                            "Page range out of bounds: " + token);
                }
                for (int p = start; p <= end; p++) {
                    if (!pages.contains(p)) pages.add(p);
                }
            } else {
                int page = Integer.parseInt(token);
                if (page < 1 || page > totalPages) {
                    throw new IllegalArgumentException(
                            "Page number out of bounds: " + page);
                }
                if (!pages.contains(page)) pages.add(page);
            }
        }

        java.util.Collections.sort(pages);
        return pages;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Counts the number of color pages across all order files.
     * Returns 0 when color-page metadata is unavailable (files not yet analysed).
     *
     * @param files List of order files
     * @return Total color page count
     */
    public static int countColorPages(List<OrderFile> files) {
        if (files == null || files.isEmpty()) return 0;

        int count = 0;
        for (OrderFile file : files) {
            int colorPages = file.getColorPageCount();
            if (colorPages > 0) {
                count += colorPages;
            }
        }
        return count;
    }

    /**
     * Determines the effective page count for the order, taking the explicit
     * page-range selection into account.
     *
     * @param order The print order
     * @return Effective page count (falls back to totalPages when range is "all")
     */
    public static int getEffectivePageCount(PrintOrder order) {
        if (order == null) return 0;

        int totalPages = order.getTotalPages();
        if (totalPages <= 0) return 0;

        String range = order.getPageRange();
        if (range == null || range.trim().isEmpty()
                || "all".equalsIgnoreCase(range.trim())) {
            return totalPages;
        }

        try {
            List<Integer> pages = parsePageRange(range, totalPages);
            return pages.size();
        } catch (Exception e) {
            return totalPages;
        }
    }
}

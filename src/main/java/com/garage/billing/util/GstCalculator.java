package com.garage.billing.util;

import com.garage.billing.model.Bill;
import com.garage.billing.model.BillServiceItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class GstCalculator {

    /** Total GST rate (intra-state Maharashtra: 9% CGST + 9% SGST). */
    public static final BigDecimal CGST_RATE = new BigDecimal("9.00");
    public static final BigDecimal SGST_RATE = new BigDecimal("9.00");
    public static final BigDecimal GST_RATE  = new BigDecimal("18.00");
    public static final BigDecimal GST_MULTIPLIER = new BigDecimal("1.18");

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");

    private GstCalculator() {}

    public static GstBreakdown calculate(BigDecimal subtotal, BigDecimal discount) {
        if (subtotal == null) {
            subtotal = BigDecimal.ZERO;
        }
        if (discount == null) {
            discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }

        BigDecimal taxable = subtotal.subtract(discount);
        if (taxable.compareTo(BigDecimal.ZERO) < 0) {
            taxable = BigDecimal.ZERO;
        }

        BigDecimal cgst = taxable.multiply(CGST_RATE)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal sgst = taxable.multiply(SGST_RATE)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = taxable.add(cgst).add(sgst)
                .setScale(2, RoundingMode.HALF_UP);

        return new GstBreakdown(subtotal, discount, taxable, cgst, sgst, grandTotal);
    }

    /**
     * Resolves GST for display/PDF using line items and the stored bill total.
     * Handles legacy bills where total_amount was saved before GST was applied.
     */
    public static GstBreakdown resolve(BigDecimal subtotal, BigDecimal discount,
                                       BigDecimal storedTotal) {
        GstBreakdown fromLines = calculate(subtotal, discount);

        if (storedTotal == null || storedTotal.compareTo(BigDecimal.ZERO) == 0) {
            return fromLines;
        }

        if (fromLines.getGrandTotal().subtract(storedTotal).abs().compareTo(TOLERANCE) <= 0) {
            return fromLines;
        }

        // Legacy bill: total in DB equals pre-GST taxable amount
        if (subtotal.compareTo(BigDecimal.ZERO) > 0
                && storedTotal.subtract(fromLines.getTaxableAmount()).abs().compareTo(TOLERANCE) <= 0) {
            return fromLines;
        }

        // No line items but stored total exists — reverse-calculate from inclusive total
        if (subtotal.compareTo(BigDecimal.ZERO) == 0) {
            return calculateFromInclusiveTotal(storedTotal, discount);
        }

        // Stored total is GST-inclusive but differs slightly — derive from stored total
        if (storedTotal.compareTo(fromLines.getTaxableAmount()) > 0) {
            return calculateFromInclusiveTotal(storedTotal, discount, subtotal);
        }

        return fromLines;
    }

    public static GstBreakdown resolveForBill(Bill bill) {
        BigDecimal subtotal = sumServiceAmounts(bill != null ? bill.getServices() : null);
        BigDecimal discount = bill != null ? bill.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal stored   = bill != null ? bill.getTotalAmount() : null;
        return resolve(subtotal, discount, stored);
    }

    public static BigDecimal sumServiceAmounts(List<BillServiceItem> services) {
        if (services == null || services.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return services.stream()
                .map(BillServiceItem::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static GstBreakdown calculateFromInclusiveTotal(BigDecimal inclusiveTotal,
                                                            BigDecimal discount) {
        return calculateFromInclusiveTotal(inclusiveTotal, discount, null);
    }

    private static GstBreakdown calculateFromInclusiveTotal(BigDecimal inclusiveTotal,
                                                            BigDecimal discount,
                                                            BigDecimal knownSubtotal) {
        if (discount == null) {
            discount = BigDecimal.ZERO;
        }

        BigDecimal taxable = inclusiveTotal.divide(GST_MULTIPLIER, 2, RoundingMode.HALF_UP);
        BigDecimal cgst = taxable.multiply(CGST_RATE)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal sgst = taxable.multiply(SGST_RATE)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        BigDecimal subtotal = knownSubtotal != null && knownSubtotal.compareTo(BigDecimal.ZERO) > 0
                ? knownSubtotal
                : taxable.add(discount);

        return new GstBreakdown(subtotal, discount, taxable, cgst, sgst, inclusiveTotal);
    }

    public static final class GstBreakdown {
        private final BigDecimal subtotal;
        private final BigDecimal discount;
        private final BigDecimal taxableAmount;
        private final BigDecimal cgstAmount;
        private final BigDecimal sgstAmount;
        private final BigDecimal grandTotal;

        public GstBreakdown(BigDecimal subtotal, BigDecimal discount,
                            BigDecimal taxableAmount, BigDecimal cgstAmount,
                            BigDecimal sgstAmount, BigDecimal grandTotal) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.taxableAmount = taxableAmount;
            this.cgstAmount = cgstAmount;
            this.sgstAmount = sgstAmount;
            this.grandTotal = grandTotal;
        }

        public BigDecimal getSubtotal() { return subtotal; }
        public BigDecimal getDiscount() { return discount; }
        public BigDecimal getTaxableAmount() { return taxableAmount; }
        public BigDecimal getCgstAmount() { return cgstAmount; }
        public BigDecimal getSgstAmount() { return sgstAmount; }
        public BigDecimal getGrandTotal() { return grandTotal; }

        public BigDecimal getTotalGstAmount() {
            return cgstAmount.add(sgstAmount);
        }
    }
}

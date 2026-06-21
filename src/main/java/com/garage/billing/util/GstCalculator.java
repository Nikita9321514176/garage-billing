package com.garage.billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GstCalculator {

    public static final BigDecimal CGST_RATE = new BigDecimal("9.00");
    public static final BigDecimal SGST_RATE = new BigDecimal("9.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

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
    }
}

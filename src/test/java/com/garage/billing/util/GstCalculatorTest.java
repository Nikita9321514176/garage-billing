package com.garage.billing.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GstCalculatorTest {

    @Test
    void calculate_appliesEighteenPercentGst() {
        GstCalculator.GstBreakdown gst =
                GstCalculator.calculate(new BigDecimal("1000.00"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("1000.00"), gst.getSubtotal());
        assertEquals(new BigDecimal("1000.00"), gst.getTaxableAmount());
        assertEquals(new BigDecimal("90.00"), gst.getCgstAmount());
        assertEquals(new BigDecimal("90.00"), gst.getSgstAmount());
        assertEquals(new BigDecimal("180.00"), gst.getTotalGstAmount());
        assertEquals(new BigDecimal("1180.00"), gst.getGrandTotal());
    }

    @Test
    void calculate_appliesDiscountBeforeGst() {
        GstCalculator.GstBreakdown gst =
                GstCalculator.calculate(new BigDecimal("1000.00"), new BigDecimal("100.00"));

        assertEquals(new BigDecimal("900.00"), gst.getTaxableAmount());
        assertEquals(new BigDecimal("81.00"), gst.getCgstAmount());
        assertEquals(new BigDecimal("81.00"), gst.getSgstAmount());
        assertEquals(new BigDecimal("1062.00"), gst.getGrandTotal());
    }

    @Test
    void resolve_handlesLegacyPreGstStoredTotal() {
        GstCalculator.GstBreakdown gst = GstCalculator.resolve(
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                new BigDecimal("1000.00"));

        assertEquals(new BigDecimal("90.00"), gst.getCgstAmount());
        assertEquals(new BigDecimal("90.00"), gst.getSgstAmount());
        assertEquals(new BigDecimal("1180.00"), gst.getGrandTotal());
    }

    @Test
    void resolve_handlesStoredInclusiveTotalWithoutLineItems() {
        GstCalculator.GstBreakdown gst = GstCalculator.resolve(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1180.00"));

        assertEquals(new BigDecimal("1000.00"), gst.getTaxableAmount());
        assertEquals(new BigDecimal("90.00"), gst.getCgstAmount());
        assertEquals(new BigDecimal("90.00"), gst.getSgstAmount());
        assertEquals(new BigDecimal("1180.00"), gst.getGrandTotal());
    }
}

package com.garage.billing.config;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

// This is the ONLY file you need to edit to change
// your garage's identity across the entire system.
@Component
public class GarageConfig {

    // ══════════════════════════════════════════════════════
    // GARAGE IDENTITY
    // ══════════════════════════════════════════════════════

    public static final String GARAGE_NAME = "The J Motors";

    public static final String ADDRESS_LINE_1 =
        "Om Shanti chawk near New Horzion School, Aanand Nagar GB Road.";

    public static final String ADDRESS_LINE_2 =
        "Thane(W), Maharashtra 400607";

    public static final String PHONE = "7303666143";

    public static final String EMAIL = "";

    // GST registration number — appears on PDF invoice.
    // You confirmed The J Motors IS GST registered.
    // Fill in your actual 15-character GSTIN below.
    public static final String GST_NUMBER = "";
        // Example format: 27XXXXX1234X1Z5
        // Leave as "" only if not yet registered —
        // GST section on invoice will then show 0%/blank.

    public static final String WEBSITE =
        "https://the-j-motors.localo.site/";

    public static final String TAGLINE = "";

    public static final String FOOTER_MESSAGE =
        "Thank you for choosing " + GARAGE_NAME
        + "! We look forward to serving you again.";

    // ══════════════════════════════════════════════════════
    // NEW: GST TAX RATES
    // ══════════════════════════════════════════════════════
    // Standard rate for vehicle repair/service in India is 18%,
    // split equally between Central and State government:
    //   CGST = 9%  (Central GST)
    //   SGST = 9%  (State GST — applies since customer & garage
    //               are both in Maharashtra)
    // If GST_NUMBER above is blank, PdfService treats the garage
    // as not-yet-registered and SKIPS tax calculation automatically
    // (shows Rs. 0.00 for GST) — change GST_NUMBER above when you
    // receive your actual GSTIN to activate real tax calculation.
    public static final BigDecimal CGST_RATE = new BigDecimal("9.00");
    public static final BigDecimal SGST_RATE = new BigDecimal("9.00");

    // ══════════════════════════════════════════════════════
    // NEW: MECHANIC / SERVICE ADVISOR
    // ══════════════════════════════════════════════════════
    // You confirmed: single fixed name for now, same on every
    // invoice. Change this value any time — no other file needs
    // editing. If you later want a per-bill mechanic, this is the
    // single place a future enhancement would replace with a
    // database-backed field.
    public static final String MECHANIC_NAME = "Rahul";

    // ══════════════════════════════════════════════════════
    // NEW: AUTHORIZED SIGNATORY NAME
    // ══════════════════════════════════════════════════════
    // Printed above the "Authorized Signature" line on the PDF.
    public static final String AUTHORIZED_SIGNATORY = GARAGE_NAME;
 // ══════════════════════════════════════════════════════
    // NEW: AUTHORIZED SIGNATORY NAME
    // ══════════════════════════════════════════════════════
    // The actual person's name printed above "Authorized
    // Signature / For The J Motors" on the PDF invoice.
    // Change this any time the signatory changes — no other
    // file needs editing.
    public static final String AUTHORIZED_SIGNATORY_NAME = "Rahul Jaiswar";
}

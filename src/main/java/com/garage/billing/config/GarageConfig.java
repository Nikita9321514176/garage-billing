package com.garage.billing.config;

import org.springframework.stereotype.Component;

// This is the ONLY file you need to edit to change
// your garage's identity across the entire system.
//
// After changing these values:
// - Every PDF invoice shows your real name and address
// - Every WhatsApp message says your garage name
// - The PDF footer shows your real website as a clickable link
// - The PDF header shows your real GST number
//
// You do NOT need to change any other Java file.
@Component
public class GarageConfig {

    // ══════════════════════════════════════════════════════
    // CHANGE THESE TO YOUR REAL GARAGE DETAILS
    // ══════════════════════════════════════════════════════

    // Your garage / workshop name — appears at top of every PDF
    public static final String GARAGE_NAME =
        "The J Motors";
        // Example: "Sharma Auto Garage"
        //          "Mumbai Car Service Centre"
        //          "Quick Fix Workshop"

    // First line of address — street and area
    public static final String ADDRESS_LINE_1 =
        "Om Shanti chawk near New Horzion School, Aanand Nagar GB Road.";
        // Example: "Shop No. 12, Industrial Area"
        //          "Opp. Bus Stand, Station Road"

    // Second line of address — city, state, PIN
    public static final String ADDRESS_LINE_2 =
        "Thane(W), Maharashtra 400607";
        // Example: "Mumbai, Maharashtra — 400001"
        //          "Nashik, Maharashtra — 422001"

    // Your garage's main phone number
    // This appears on the PDF and in WhatsApp messages
    public static final String PHONE =
        "7303666143";
        // Use your actual mobile or landline
        // Example: "9800001234"

    // Your garage email address (optional — leave blank if none)
    public static final String EMAIL =
        "";
        // If you don't have an email just put blank: ""

    // GST registration number — appears on PDF invoice
    // If you are not GST registered, put blank: ""
    public static final String GST_NUMBER =
        "";
        // Example GST format: 27XXXXX1234X1Z5
        // The 27 at the start = Maharashtra state code

    // Your garage website — appears as a clickable link
    // in the PDF footer. If no website, put blank: ""
    public static final String WEBSITE =
        "https://the-j-motors.localo.site/";
        // Example: "www.sharmagarage.com"
        // Or leave blank: ""

    // Short tagline below the garage name on PDF
    public static final String TAGLINE =
        "";
        // Example: "Trusted Workshop Since 2010"
        //          "All Car Makes & Models Serviced"

    // Footer message printed at the bottom of every PDF
    // Keep it short — one or two sentences max
    public static final String FOOTER_MESSAGE =
        "Thank you for choosing "
        + GARAGE_NAME
        + "! We look forward to serving you again.";
        // This automatically uses your GARAGE_NAME above
}
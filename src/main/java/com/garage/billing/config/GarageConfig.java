package com.garage.billing.config;

// @Component makes this a Spring bean so it can be
// @Autowired anywhere — especially in PdfService
import org.springframework.stereotype.Component;

// This class holds all garage-specific details.
// To change your garage name/address/phone, edit ONLY this file.
// Nothing else in the codebase needs to change.
@Component
public class GarageConfig {

    // ── CHANGE THESE TO YOUR REAL GARAGE DETAILS ──────────

    // Your garage / workshop name
    public static final String GARAGE_NAME =
        "The J Motors";

    // Full address — shown on PDF invoice
    public static final String ADDRESS_LINE_1 =
        "Aanand Nagar, Om Shanti Chawk, Ghodbunder Rd, near new Horzion School";
    public static final String ADDRESS_LINE_2 =
        "Thane(W), Maharashtra — 400607";

    // Contact details
    public static final String PHONE =
        "7303666143";

    public static final String EMAIL =
        "";

    // GST number — leave blank if not registered
    public static final String GST_NUMBER =
        "";

    // Website — shown as clickable link in PDF footer
    public static final String WEBSITE =
        "https://the-j-motors.localo.site/";

    // Tagline shown below garage name on invoice
    public static final String TAGLINE =
        "";

    // Footer thank-you message on PDF
    public static final String FOOTER_MESSAGE =
        "Thank you for choosing " + GARAGE_NAME
        + "! We look forward to serving you again.";
}
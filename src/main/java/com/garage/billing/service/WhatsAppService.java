package com.garage.billing.service;

import com.garage.billing.model.Bill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WhatsAppService {

    // Base URL of your deployed app.
    // Set this in application.properties:
    //   app.base.url=https://the-j-motors.localo.site
    // For local testing it defaults to localhost:8080
    @Value("${app.base.url:http://localhost:8080}")
    private String baseUrl;

    // ─────────────────────────────────────────────────────
    // BUILD WHATSAPP MESSAGE WITH DIRECT PDF LINK
    //
    // CHANGED: link now points to /invoice/{billNumber}/pdf
    // instead of /invoice/{billNumber}.
    //
    // OLD behavior: link opened the HTML preview page first
    // (invoice_view.html), customer had to click "Download
    // PDF Invoice" on that page to get the actual PDF — two
    // clicks, two page loads, and the HTML preview's styling
    // looked similar enough to the PDF that it felt like a
    // confusing duplicate document.
    //
    // NEW behavior: link goes straight to the PDF endpoint.
    // One click, one document, no intermediate page. This
    // matches the real-world pattern most garages/workshops
    // use — customer taps the WhatsApp link and the invoice
    // PDF opens immediately in their phone's browser/PDF viewer.
    //
    // The HTML preview page (InvoiceController.viewPublicInvoice,
    // invoice_view.html) still exists and still works if you
    // ever want to link to it from somewhere else (e.g. a
    // "View Invoice Online" button elsewhere in the app) — it
    // is simply no longer the link sent via WhatsApp.
    // ─────────────────────────────────────────────────────
    public String buildBillMessage(Bill bill) {

        // CHANGED: /pdf suffix added — direct PDF link, no HTML page
        String invoiceUrl = baseUrl + "/invoice/" + bill.getBillNumber() + "/pdf";

        String customerName = bill.getCustomerName() != null
                ? bill.getCustomerName().split(" ")[0]
                : "Customer";

        return "Hello " + customerName + ",\n\n"
             + "Your service invoice is ready.\n\n"
             + "Invoice No: " + bill.getBillNumber() + "\n\n"
             + "View Invoice:\n"
             + invoiceUrl + "\n\n"
             + "Thank you for choosing The J Motors.\n\n"
             + "\uD83D\uDCDE 7303666143";
    }

    // ─────────────────────────────────────────────────────
    // BUILD wa.me URL (opens WhatsApp with message) — unchanged
    // ─────────────────────────────────────────────────────
    public String buildBillUrl(Bill bill) {
        String phone = bill.getCustomerPhone();
        String message = buildBillMessage(bill);
        return buildWaUrl(phone, message);
    }

    public String buildWaUrl(String phone, String message) {
        String cleanPhone = phone != null
                ? phone.replaceAll("[^0-9]", "") : "";

        if (cleanPhone.length() == 10) {
            cleanPhone = "91" + cleanPhone;
        }

        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        return "https://wa.me/" + cleanPhone + "?text=" + encoded;
    }
}
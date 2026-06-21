package com.garage.billing.service;

import com.garage.billing.model.Bill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    // BUILD WHATSAPP MESSAGE WITH PUBLIC INVOICE LINK
    // ─────────────────────────────────────────────────────
    public String buildBillMessage(Bill bill) {

        // Public invoice URL — customer opens this without login
        String invoiceUrl = baseUrl + "/invoice/" + bill.getBillNumber();

        // Customer first name only (friendlier)
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
    // BUILD wa.me URL (opens WhatsApp with message)
    // ─────────────────────────────────────────────────────
    public String buildBillUrl(Bill bill) {
        String phone = bill.getCustomerPhone();
        String message = buildBillMessage(bill);
        return buildWaUrl(phone, message);
    }

    public String buildWaUrl(String phone, String message) {
        // Clean phone: remove spaces, dashes, +
        String cleanPhone = phone != null
                ? phone.replaceAll("[^0-9]", "") : "";

        // Add India country code if not present
        if (cleanPhone.length() == 10) {
            cleanPhone = "91" + cleanPhone;
        }

        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        return "https://wa.me/" + cleanPhone + "?text=" + encoded;
    }
}
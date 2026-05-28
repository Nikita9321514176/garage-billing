package com.garage.billing.service;

import com.garage.billing.config.GarageConfig;
import com.garage.billing.model.Bill;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// @Service = Spring-managed bean
@Service
public class WhatsAppService {

    // ════════════════════════════════════════════════════════
    // METHOD 1 — wa.me Link Builder
    // Completely FREE WhatsApp integration
    // Opens WhatsApp with pre-filled message
    // ════════════════════════════════════════════════════════

    // Build WhatsApp URL for a bill
    public String buildBillUrl(Bill bill) {

        String message = buildBillMessage(bill);

        return buildWaUrl(
            bill.getCustomerPhone(),
            message
        );
    }

    // Build WhatsApp URL for payment reminder
    public String buildReminderUrl(
            String customerPhone,
            String customerName,
            String billNumber,
            java.math.BigDecimal balance) {

        String message = buildReminderMessage(
            customerName,
            billNumber,
            balance
        );

        return buildWaUrl(customerPhone, message);
    }

    // ════════════════════════════════════════════════════════
    // CORE wa.me URL BUILDER
    // ════════════════════════════════════════════════════════

    public String buildWaUrl(
            String phone,
            String message) {

        // Safety check
        if (phone == null || phone.trim().isEmpty()) {
            return "#";
        }

        // Remove spaces, dashes, brackets etc.
        String cleaned = phone.replaceAll("[^0-9]", "");

        // Remove leading 0
        // Example: 09876543210 → 9876543210
        if (cleaned.startsWith("0")) {
            cleaned = cleaned.substring(1);
        }

        // Add India country code if needed
        if (cleaned.length() == 10) {
            cleaned = "91" + cleaned;
        }

        // Encode message for URL
        String encodedMessage;

        try {
            encodedMessage = URLEncoder.encode(
                message,
                StandardCharsets.UTF_8
            );
        } catch (Exception e) {

            // fallback
            encodedMessage = message.replace(" ", "+");
        }

        // Final wa.me URL
        return "https://wa.me/"
                + cleaned
                + "?text="
                + encodedMessage;
    }

    // ════════════════════════════════════════════════════════
    // BILL MESSAGE
    // ════════════════════════════════════════════════════════

    public String buildBillMessage(Bill bill) {

        StringBuilder sb = new StringBuilder();

        sb.append("Dear ");

        sb.append(
            bill.getCustomerName() != null
            ? bill.getCustomerName()
            : "Customer"
        );

        sb.append(",\n\n");

        sb.append("Your bill ");

        sb.append(bill.getBillNumber());

        sb.append(" from ");

        sb.append(GarageConfig.GARAGE_NAME);

        sb.append(" is ready.\n\n");

        sb.append("*Bill Details:*\n");

        sb.append("Car: ");

        sb.append(bill.getCarNumber());

        sb.append("\n");

        sb.append("Total: Rs.");

        sb.append(
            bill.getTotalAmount().toBigInteger()
        );

        sb.append("\n");

        sb.append("Paid: Rs.");

        sb.append(
            bill.getPaidAmount().toBigInteger()
        );

        sb.append("\n");

        sb.append("Balance: Rs.");

        sb.append(
            bill.getBalanceAmount().toBigInteger()
        );

        sb.append("\n\n");

        // Pending payment message
        if (bill.getBalanceAmount() != null
                && bill.getBalanceAmount()
                    .compareTo(
                        java.math.BigDecimal.ZERO
                    ) > 0) {

            sb.append(
                "Please pay the balance amount "
                + "at your earliest convenience.\n\n"
            );

        } else {

            sb.append(
                "Thank you for your payment!\n\n"
            );
        }

        sb.append("Thank you for choosing ");

        sb.append(GarageConfig.GARAGE_NAME);

        sb.append("!");

        return sb.toString();
    }

    // ════════════════════════════════════════════════════════
    // PAYMENT REMINDER MESSAGE
    // ════════════════════════════════════════════════════════

    public String buildReminderMessage(
            String customerName,
            String billNumber,
            java.math.BigDecimal balance) {

        return "Dear " + customerName + ",\n\n"

            + "This is a gentle reminder that your "
            + "bill "

            + billNumber

            + " at "

            + GarageConfig.GARAGE_NAME

            + " has a pending balance of Rs."

            + (balance != null
                ? balance.toBigInteger()
                : "0")

            + ".\n\n"

            + "Please pay at your earliest "
            + "convenience.\n\n"

            + "Thank you!\n"

            + GarageConfig.GARAGE_NAME;
    }
}
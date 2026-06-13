package com.garage.billing.service;

import com.garage.billing.config.GarageConfig;
import com.garage.billing.model.Bill;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WhatsAppService {

    // =====================================================
    // BILL URL
    // =====================================================

    public String buildBillUrl(Bill bill) {

        String message = buildBillMessage(bill);

        return buildWaUrl(
                bill.getCustomerPhone(),
                message
        );
    }

    // =====================================================
    // REMINDER URL
    // =====================================================

    public String buildReminderUrl(
            String customerPhone,
            String customerName,
            String billNumber,
            BigDecimal balance) {

        String message =
                buildReminderMessage(
                        customerName,
                        billNumber,
                        balance
                );

        return buildWaUrl(
                customerPhone,
                message
        );
    }

    // =====================================================
    // WA.ME URL BUILDER
    // =====================================================

    public String buildWaUrl(
            String phone,
            String message) {

        if (phone == null || phone.trim().isEmpty()) {
            return "#";
        }

        String cleaned =
                phone.replaceAll("[^0-9]", "");

        if (cleaned.startsWith("0")) {
            cleaned = cleaned.substring(1);
        }

        if (cleaned.length() == 10) {
            cleaned = "91" + cleaned;
        }

        String encodedMessage;

        try {

            encodedMessage =
                    URLEncoder.encode(
                            message,
                            StandardCharsets.UTF_8
                    );

        } catch (Exception e) {

            encodedMessage =
                    message.replace(" ", "+");
        }

        return "https://wa.me/"
                + cleaned
                + "?text="
                + encodedMessage;
    }

    // =====================================================
    // MAIN BILL MESSAGE
    // =====================================================

    public String buildBillMessage(Bill bill) {

        String customerName =
                bill.getCustomerName() != null
                        ? bill.getCustomerName()
                        : "Customer";

        String vehicleNumber =
                bill.getCarNumber() != null
                        ? bill.getCarNumber()
                        : "-";

        String billNo =
                bill.getBillNumber() != null
                        ? bill.getBillNumber()
                        : "-";

        BigDecimal total =
                bill.getTotalAmount() != null
                        ? bill.getTotalAmount()
                        : BigDecimal.ZERO;

        BigDecimal paid =
                bill.getPaidAmount() != null
                        ? bill.getPaidAmount()
                        : BigDecimal.ZERO;

        BigDecimal balance =
                bill.getBalanceAmount() != null
                        ? bill.getBalanceAmount()
                        : BigDecimal.ZERO;

        String status =
                bill.getPaymentStatus() != null
                        ? bill.getPaymentStatus()
                        : "PENDING";

        StringBuilder sb = new StringBuilder();

        // =================================================
        // PAID
        // =================================================

        if ("PAID".equalsIgnoreCase(status)
                || balance.compareTo(BigDecimal.ZERO) <= 0) {

            sb.append("Hello ")
                    .append(customerName)
                    .append(",\n\n");

            sb.append("Thank you for visiting ")
                    .append(GarageConfig.GARAGE_NAME)
                    .append(".\n\n");

            sb.append("Your vehicle service bill has been successfully settled.\n\n");

            sb.append("Bill No: ")
                    .append(billNo)
                    .append("\n");

            sb.append("Vehicle: ")
                    .append(vehicleNumber)
                    .append("\n");

            sb.append("Total Bill Amount: ₹")
                    .append(total.setScale(0, RoundingMode.HALF_UP))
                    .append("\n");

            sb.append("Outstanding Balance: ₹0\n\n");

            sb.append("Thank you for choosing ")
                    .append(GarageConfig.GARAGE_NAME)
                    .append(".\n\n");

            sb.append("For any queries, please contact us.\n\n");

            sb.append("Regards,\n");
            sb.append(GarageConfig.GARAGE_NAME);

            return sb.toString();
        }

        // =================================================
        // PARTIAL
        // =================================================

        if ("PARTIAL".equalsIgnoreCase(status)) {

            sb.append("Hello ")
                    .append(customerName)
                    .append(",\n\n");

            sb.append("We hope you are doing well.\n\n");

            sb.append("A balance is pending for the service work completed on your vehicle.\n\n");

            sb.append("Bill No: ")
                    .append(billNo)
                    .append("\n");

            sb.append("Vehicle: ")
                    .append(vehicleNumber)
                    .append("\n");

            sb.append("Total Amount: ₹")
                    .append(total.setScale(0, RoundingMode.HALF_UP))
                    .append("\n");

            sb.append("Paid Amount: ₹")
                    .append(paid.setScale(0, RoundingMode.HALF_UP))
                    .append("\n");

            sb.append("Balance Due: ₹")
                    .append(balance.setScale(0, RoundingMode.HALF_UP))
                    .append("\n\n");

            sb.append("Kindly clear the pending amount at your convenience.\n\n");

            sb.append("Thank you for choosing ")
                    .append(GarageConfig.GARAGE_NAME)
                    .append(".\n\n");

            sb.append("Regards,\n");
            sb.append(GarageConfig.GARAGE_NAME);

            return sb.toString();
        }

        // =================================================
        // PENDING
        // =================================================

        sb.append("Hello ")
                .append(customerName)
                .append(",\n\n");

        sb.append("This is a reminder regarding the pending payment for your vehicle service bill.\n\n");

        sb.append("Bill No: ")
                .append(billNo)
                .append("\n");

        sb.append("Vehicle: ")
                .append(vehicleNumber)
                .append("\n");

        sb.append("Balance Due: ₹")
                .append(balance.setScale(0, RoundingMode.HALF_UP))
                .append("\n\n");

        sb.append("We request you to clear the outstanding amount at your convenience.\n\n");

        sb.append("Thank you for your continued trust in ")
                .append(GarageConfig.GARAGE_NAME)
                .append(".\n\n");

        sb.append("Regards,\n");
        sb.append(GarageConfig.GARAGE_NAME);

        return sb.toString();
    }

    // =====================================================
    // REMINDER MESSAGE
    // =====================================================

    public String buildReminderMessage(
            String customerName,
            String billNumber,
            BigDecimal balance) {

        return "Hello "
                + customerName
                + ",\n\n"
                + "This is a reminder regarding Bill "
                + billNumber
                + ".\n\n"
                + "Outstanding Balance: ₹"
                + (balance != null
                ? balance.setScale(0, RoundingMode.HALF_UP)
                : "0")
                + "\n\n"
                + "Kindly clear the pending amount at your convenience.\n\n"
                + "Regards,\n"
                + GarageConfig.GARAGE_NAME;
    }
}
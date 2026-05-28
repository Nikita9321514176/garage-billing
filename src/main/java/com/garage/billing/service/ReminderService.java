package com.garage.billing.service;

import com.garage.billing.model.Bill;
import com.garage.billing.model.Reminder;
import com.garage.billing.repository.BillRepository;
import com.garage.billing.repository.ReminderRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class ReminderService {

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private BillRepository billRepository;

    // ── GET PENDING REMINDERS ───────────────────────────────
    // Used by dashboard and reminders page
    public List<Reminder> getPendingReminders() {
        return reminderRepository.findPendingWithDetails();
    }

    // ── GET ALL REMINDERS (history) ─────────────────────────
    public List<Reminder> getAllReminders() {
        return reminderRepository.findAllWithDetails();
    }

    // ── STATS ───────────────────────────────────────────────
    public Map<String, Object> getReminderStats() {
        return reminderRepository.getReminderStats();
    }

    // ── CREATE REMINDER FOR ONE BILL ────────────────────────
    // Called manually or by the auto-scan method below
    public Reminder createReminder(Long billId,
                                    String message) {
        // Don't create duplicate — if PENDING already exists, skip
        if (reminderRepository.existsPendingForBill(billId)) {
            // Return null to signal "already exists"
            return null;
        }
        Reminder r = Reminder.builder()
            .billId(billId)
            .status("PENDING")
            .message(message)
            .build();
        Long id = reminderRepository.save(r);
        r.setId(id);
        return r;
    }

    // ── AUTO-SCAN AND CREATE REMINDERS ──────────────────────
    // Finds all overdue bills and creates a reminder for each
    // one that doesn't already have a PENDING reminder.
    // Called by the controller when owner clicks "Scan overdue bills".
    public int createRemindersForAllOverdueBills() {
        List<Bill> overdueBills = billRepository.findOverdue();
        int created = 0;

        for (Bill bill : overdueBills) {
            // Build default reminder message
            String message = buildDefaultMessage(
                bill.getCustomerName(),
                bill.getBillNumber(),
                bill.getTotalAmount(),
                bill.getBalanceAmount()
            );
            Reminder result = createReminder(
                bill.getId(), message);
            if (result != null) created++;
        }
        return created;
        // Returns how many NEW reminders were created
        // (existing ones were skipped)
    }

    // ── BUILD WHATSAPP URL ──────────────────────────────────
    // Creates a wa.me link with the reminder message pre-filled.
    // When clicked on mobile: opens WhatsApp with message ready.
    // Format: https://wa.me/91XXXXXXXXXX?text=URL_ENCODED_MSG
    public String buildWhatsAppUrl(Reminder reminder) {
        String message = buildDefaultMessage(
            reminder.getCustomerName(),
            reminder.getBillNumber(),
            null,   // total not always available in reminder
            reminder.getBalanceAmount()
        );

        String encodedMsg;
        try {
            encodedMsg = URLEncoder.encode(
                message, StandardCharsets.UTF_8);
        } catch (Exception e) {
            encodedMsg = message.replace(" ", "+");
        }

        // Clean phone number for WhatsApp:
        // Remove leading 0 if present → WhatsApp needs country code
        // Indian numbers: 9876543210 → wa.me/919876543210
        String phone = reminder.getCustomerPhone();
        if (phone == null) return "#";

        // Remove any spaces, dashes, brackets
        phone = phone.replaceAll("[^0-9]", "");

        // Remove leading 0 (0XXXXXXXXXX → XXXXXXXXXX)
        if (phone.startsWith("0")) {
            phone = phone.substring(1);
        }
        // Add country code if not already 12 digits (with 91)
        if (phone.length() == 10) {
            phone = "91" + phone;
        }

        return "https://wa.me/" + phone
             + "?text=" + encodedMsg;
    }

    // ── BUILD DEFAULT MESSAGE TEXT ──────────────────────────
    // Constructs the reminder SMS/WhatsApp message text.
    // Keeps it short and professional.
    public String buildDefaultMessage(
            String customerName,
            String billNumber,
            BigDecimal totalAmount,
            BigDecimal balanceAmount) {

        StringBuilder msg = new StringBuilder();
        msg.append("Dear ").append(
            customerName != null ? customerName : "Customer");
        msg.append(", your bill ");
        msg.append(billNumber != null ? billNumber : "");

        if (totalAmount != null) {
            msg.append(" of Rs.").append(
                totalAmount.setScale(0,
                    java.math.RoundingMode.HALF_UP));
        }

        msg.append(" has a pending balance of Rs.");
        msg.append(balanceAmount != null
            ? balanceAmount.setScale(0,
                java.math.RoundingMode.HALF_UP)
            : "0");
        msg.append(". Please visit us or contact for payment.");
        msg.append(" Thank you! - Garage");

        return msg.toString();
    }

    // ── MARK SENT ────────────────────────────────────────────
    public void markSent(Long reminderId) {
        reminderRepository.updateStatus(reminderId, "SENT");
    }

    // ── MARK IGNORED ─────────────────────────────────────────
    public void markIgnored(Long reminderId) {
        reminderRepository.updateStatus(reminderId, "IGNORED");
    }

    // ── DELETE ───────────────────────────────────────────────
    public void delete(Long reminderId) {
        reminderRepository.deleteById(reminderId);
    }
}
package com.garage.billing.controller;

import com.garage.billing.model.Bill;
import com.garage.billing.model.Payment;
import com.garage.billing.service.BillService;
import com.garage.billing.service.PaymentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/payment")
public class PaymentController {

    @Autowired private PaymentService paymentService;
    @Autowired private BillService    billService;

    // ── PAYMENT TRACKING PAGE ──────────────────────────────
    // URL: GET /payment/tracking
    // URL: GET /payment/tracking?status=PENDING
    // URL: GET /payment/tracking?status=OVERDUE
    @GetMapping("/tracking")
    public String paymentTracking(
            // status filter — default "ALL" shows everything
            @RequestParam(value = "status",
                          defaultValue = "ALL") String status,
            Model model
    ) {
        // ── Stat cards ──────────────────────────────────────
        try {
            Map<String, Object> stats =
                paymentService.getPaymentTrackingStats();
            model.addAttribute("trackingStats", stats);
        } catch (Exception e) {
            model.addAttribute("trackingStats",
                Collections.emptyMap());
        }

        // ── Collected today ─────────────────────────────────
        try {
            model.addAttribute("collectedToday",
                paymentService.getCollectedToday());
        } catch (Exception e) {
            model.addAttribute("collectedToday",
                BigDecimal.ZERO);
        }

        // ── Bill list (filtered) ────────────────────────────
        try {
            List<Map<String, Object>> bills =
                paymentService.getAllBillsWithPaymentStatus(status);
            model.addAttribute("bills", bills);
            model.addAttribute("billCount", bills.size());
        } catch (Exception e) {
            model.addAttribute("bills", Collections.emptyList());
            model.addAttribute("billCount", 0);
        }

        // Pass active filter back to template
        // so the correct filter button appears highlighted
        model.addAttribute("activeFilter", status.toUpperCase());

        return "payment/tracking";
    }

    // ── RECORD PAYMENT ─────────────────────────────────────
    // URL: POST /payment/save
    // Called from bill/detail.html "Record Payment" form
    @PostMapping("/save")
    public String recordPayment(
            @RequestParam Long       billId,
            @RequestParam BigDecimal amountPaid,
            @RequestParam String     paymentMode,
            @RequestParam(required = false) String transactionReference,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes
    ) {
        try {
            // Validate amount is positive
            if (amountPaid == null
                    || amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException(
                    "Payment amount must be greater than zero");
            }

            // Get the bill to check it exists and
            // that we are not overpaying
            Optional<Bill> billOpt = billService.findById(billId);
            if (billOpt.isEmpty()) {
                throw new RuntimeException(
                    "Bill not found: " + billId);
            }

            Bill bill = billOpt.get();

            // Prevent overpayment — cap at balance amount
            if (amountPaid.compareTo(bill.getBalanceAmount()) > 0) {
                throw new RuntimeException(
                    "Payment amount ₹" + amountPaid
                    + " exceeds balance ₹" + bill.getBalanceAmount()
                    + ". Please enter correct amount.");
            }

            Payment payment = Payment.builder()
                .billId(billId)
                .amountPaid(amountPaid)
                .paymentMode(paymentMode)
                .transactionReference(
                    transactionReference != null
                        && !transactionReference.trim().isEmpty()
                        ? transactionReference.trim() : null)
                .notes(notes != null
                        && !notes.trim().isEmpty()
                        ? notes.trim() : null)
                .build();

            // recordPayment() saves payment AND
            // recalculates bill balance + status atomically
            paymentService.recordPayment(payment);

            redirectAttributes.addFlashAttribute("successMessage",
                "Payment of \u20B9" + amountPaid
                + " recorded successfully via " + paymentMode + "!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Error recording payment: " + e.getMessage());
        }

        // Always go back to the bill detail page
        return "redirect:/bill/" + billId;
    }

    // ── PAYMENT HISTORY FOR A BILL (AJAX) ─────────────────
    // URL: GET /payment/history?billId=5
    // Returns JSON — used optionally for AJAX refresh
    @GetMapping("/history")
    @ResponseBody
    public List<com.garage.billing.model.Payment> paymentHistory(
            @RequestParam Long billId) {
        return paymentService.getPaymentHistory(billId);
    }
}
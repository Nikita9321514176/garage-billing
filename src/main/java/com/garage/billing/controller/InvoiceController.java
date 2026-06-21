package com.garage.billing.controller;

import com.garage.billing.model.Bill;
import com.garage.billing.model.Payment;
import com.garage.billing.service.BillService;
import com.garage.billing.service.PaymentService;
import com.garage.billing.service.PdfService;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * PUBLIC Invoice Controller — NO LOGIN REQUIRED.
 *
 * This controller serves customer-facing invoice pages.
 * Security is configured to permit /invoice/** without authentication.
 *
 * URLs:
 *   GET /invoice/{billNumber}        → public invoice view page
 *   GET /invoice/{billNumber}/pdf    → PDF download (customer-triggered)
 */
@Controller
@RequestMapping("/invoice")
public class InvoiceController {

    @Autowired private BillService    billService;
    @Autowired private PaymentService paymentService;
    @Autowired private PdfService     pdfService;

    // ─────────────────────────────────────────────────────────
    // PUBLIC INVOICE PAGE
    // GET /invoice/BILL-2026-030
    // No login required — customer opens this link from WhatsApp
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{billNumber}")
    public String viewPublicInvoice(
            @PathVariable String billNumber,
            Model model) {

        // Find bill by bill number (not ID — bill number is public-safe)
        Optional<Bill> billOpt = billService.findByBillNumber(billNumber);

        if (billOpt.isEmpty()) {
            model.addAttribute("errorMessage",
                "Invoice not found: " + billNumber);
            return "invoice/not-found";
        }

        Bill bill = billOpt.get();
        List<Payment> payments = paymentService.getPaymentHistory(bill.getId());

        model.addAttribute("bill", bill);
        model.addAttribute("gst", billService.getGstBreakdown(bill));
        model.addAttribute("payments", payments);
        model.addAttribute("billNumber", billNumber);

        return "invoice/view";
    }

    // ─────────────────────────────────────────────────────────
    // PUBLIC PDF DOWNLOAD
    // GET /invoice/BILL-2026-030/pdf
    // Customer clicks "Download PDF" button — triggers this.
    // Uses the SAME PdfService as owner — consistent output.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{billNumber}/pdf")
    public void downloadPublicPdf(
            @PathVariable String billNumber,
            HttpServletResponse response) {

        try {
            Optional<Bill> billOpt = billService.findByBillNumber(billNumber);

            if (billOpt.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("Invoice not found: " + billNumber);
                return;
            }

            Bill bill = billOpt.get();
            byte[] pdf = pdfService.generateBillPdf(bill.getId());

            response.setContentType("application/pdf");
            // inline = opens in browser PDF viewer (not auto-download)
            // Customer can then choose to save if they want
            response.setHeader("Content-Disposition",
                "inline; filename=\"Invoice-" + billNumber + ".pdf\"");
            response.setContentLength(pdf.length);
            response.getOutputStream().write(pdf);
            response.getOutputStream().flush();

        } catch (Exception e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("Error generating PDF: " + e.getMessage());
            } catch (Exception ignored) {}
        }
    }
}
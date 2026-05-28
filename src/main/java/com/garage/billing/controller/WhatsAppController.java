package com.garage.billing.controller;

import com.garage.billing.model.Bill;
import com.garage.billing.service.BillService;
import com.garage.billing.service.WhatsAppService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/whatsapp")
public class WhatsAppController {

    @Autowired
    private WhatsAppService whatsAppService;

    @Autowired
    private BillService billService;

    // ─────────────────────────────────────────────────────
    // WHATSAPP SEND PAGE
    // URL: /whatsapp/send?billId=1
    // Shows preview + Send button
    // ─────────────────────────────────────────────────────
    @GetMapping("/send")
    public String showSendPage(
            @RequestParam Long billId,
            Model model) {

        Optional<Bill> billOpt =
                billService.findById(billId);

        // Bill not found
        if (billOpt.isEmpty()) {
            model.addAttribute(
                    "errorMessage",
                    "Bill not found: " + billId);

            return "redirect:/dashboard";
        }

        Bill bill = billOpt.get();

        // Build WhatsApp message text
        String message =
                whatsAppService.buildBillMessage(bill);

        // Build wa.me URL
        String waUrl =
                whatsAppService.buildBillUrl(bill);

        // Send data to Thymeleaf page
        model.addAttribute("bill", bill);
        model.addAttribute("message", message);
        model.addAttribute("waUrl", waUrl);

        return "whatsapp/send";
    }

    // ─────────────────────────────────────────────────────
    // QUICK SEND
    // Opens WhatsApp directly
    // URL: POST /whatsapp/quick-send
    // ─────────────────────────────────────────────────────
    @PostMapping("/quick-send")
    public String quickSend(
            @RequestParam String phone,
            @RequestParam String message,
            @RequestParam(required = false)
            String returnUrl,
            RedirectAttributes redirectAttributes) {

        try {

            // Build wa.me link
            String waUrl =
                    whatsAppService.buildWaUrl(
                            phone,
                            message
                    );

            // Open WhatsApp
            return "redirect:" + waUrl;

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "WhatsApp Error: "
                            + e.getMessage()
            );

            // Fallback redirect
            if (returnUrl != null
                    && !returnUrl.isBlank()) {
                return "redirect:" + returnUrl;
            }

            return "redirect:/dashboard";
        }
    }
}
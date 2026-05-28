package com.garage.billing.controller;

import com.garage.billing.model.Reminder;
import com.garage.billing.service.ReminderService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reminder")
public class ReminderController {

    @Autowired
    private ReminderService reminderService;

    // ── REMINDERS LIST PAGE ─────────────────────────────────
    // URL: GET /reminder/list
    // URL: GET /reminder/list?filter=ALL (default)
    // URL: GET /reminder/list?filter=PENDING
    // URL: GET /reminder/list?filter=SENT
    // URL: GET /reminder/list?filter=IGNORED
    @GetMapping("/list")
    public String reminderList(
            @RequestParam(value = "filter",
                          defaultValue = "PENDING")
                String filter,
            Model model
    ) {
        // ── Stats for top cards ──────────────────────────────
        try {
            Map<String, Object> stats =
                reminderService.getReminderStats();
            model.addAttribute("reminderStats", stats);
        } catch (Exception e) {
            model.addAttribute("reminderStats",
                Collections.emptyMap());
        }

        // ── Reminder list (filtered) ─────────────────────────
        try {
            List<Reminder> reminders;

            if ("ALL".equalsIgnoreCase(filter)) {
                reminders = reminderService.getAllReminders();
            } else {
                // Get all and filter in Java
                // (small dataset so this is fine)
                reminders = reminderService
                    .getAllReminders()
                    .stream()
                    .filter(r -> filter.equalsIgnoreCase(
                        r.getStatus()))
                    .toList();
            }

            // Build WhatsApp URL for each PENDING reminder
            // We attach it as a transient property using
            // a parallel approach — store in model map
            model.addAttribute("reminders", reminders);
            model.addAttribute("reminderCount",
                reminders.size());

        } catch (Exception e) {
            model.addAttribute("reminders",
                Collections.emptyList());
            model.addAttribute("reminderCount", 0);
        }

        model.addAttribute("activeFilter",
            filter.toUpperCase());

        return "reminder/list";
    }

    // ── SCAN OVERDUE BILLS → CREATE REMINDERS ───────────────
    // URL: POST /reminder/scan
    // Owner clicks "Scan for overdue bills" button.
    // Finds all overdue bills and creates PENDING reminders
    // for any that don't already have one.
    @PostMapping("/scan")
    public String scanOverdueBills(
            RedirectAttributes redirectAttributes) {
        try {
            int created =
                reminderService
                    .createRemindersForAllOverdueBills();

            if (created > 0) {
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    created + " new reminder(s) created for "
                    + "overdue bills.");
            } else {
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "All overdue bills already have reminders.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Error scanning bills: " + e.getMessage());
        }
        return "redirect:/reminder/list";
    }

    // ── CREATE REMINDER FOR SINGLE BILL ─────────────────────
    // URL: POST /reminder/create?billId=5
    // Called from bill detail page "Create Reminder" button
    @PostMapping("/create")
    public String createReminder(
            @RequestParam Long billId,
            @RequestParam(required = false)
                String customMessage,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String message = (customMessage != null
                && !customMessage.trim().isEmpty())
                ? customMessage.trim()
                : null;
            // null message → service builds default message

            Reminder r = reminderService.createReminder(
                billId, message);

            if (r != null) {
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Reminder created successfully!");
            } else {
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "A reminder already exists for this bill.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Error: " + e.getMessage());
        }
        return "redirect:/bill/" + billId;
    }

    // ── MARK SENT ────────────────────────────────────────────
    // URL: POST /reminder/sent?id=3
    // Owner sent the WhatsApp manually → mark reminder as sent
    @PostMapping("/sent")
    public String markSent(
            @RequestParam Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            reminderService.markSent(id);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Reminder marked as sent.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                "errorMessage", e.getMessage());
        }
        return "redirect:/reminder/list";
    }

    // ── MARK IGNORED ─────────────────────────────────────────
    // URL: POST /reminder/ignore?id=3
    @PostMapping("/ignore")
    public String ignoreReminder(
            @RequestParam Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            reminderService.markIgnored(id);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Reminder dismissed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                "errorMessage", e.getMessage());
        }
        return "redirect:/reminder/list";
    }

    // ── DELETE ───────────────────────────────────────────────
    // URL: POST /reminder/delete?id=3
    @PostMapping("/delete")
    public String deleteReminder(
            @RequestParam Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            reminderService.delete(id);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Reminder deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                "errorMessage", e.getMessage());
        }
        return "redirect:/reminder/list";
    }
}
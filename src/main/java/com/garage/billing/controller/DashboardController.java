package com.garage.billing.controller;

import com.garage.billing.repository.BillRepository;
import com.garage.billing.repository.ReminderRepository;
import com.garage.billing.service.BillService;
import com.garage.billing.service.ReminderService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    @Autowired
    private BillService billService;

    @Autowired
    private ReminderService reminderService;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {

        // ── STAT CARDS ──────────────────────────────────────
        try {
            BillRepository.DashboardStats stats =
                billService.getDashboardStats();
            model.addAttribute("stats", stats);
        } catch (Exception e) {
            model.addAttribute("stats", null);
        }

        // ── OVERDUE COUNT ────────────────────────────────────
        try {
            model.addAttribute("overdueCount",
                billService.getOverdueCount());
        } catch (Exception e) {
            model.addAttribute("overdueCount", 0L);
        }

        // ── RECENT BILLS (last 8) ────────────────────────────
        try {
            model.addAttribute("recentBills",
                billService.getRecentBills(8));
        } catch (Exception e) {
            model.addAttribute("recentBills",
                Collections.emptyList());
        }

        // ── OVERDUE BILLS (for reminder section) ─────────────
        try {
            model.addAttribute("overdueBills",
                billService.getOverdueBills());
        } catch (Exception e) {
            model.addAttribute("overdueBills",
                Collections.emptyList());
        }

        // ── PAYMENT STATUS BREAKDOWN ─────────────────────────
        try {
            List<Map<String, Object>> breakdown =
                billService.getPaymentStatusBreakdown();

            // Calculate percentages for the visual bar
            // Total bills = sum of all counts across statuses
            long totalBills = breakdown.stream()
                .mapToLong(row -> {
                    Object cnt = row.get("bill_count");
                    return cnt != null
                        ? ((Number) cnt).longValue() : 0L;
                })
                .sum();

            // Build a simpler map for Thymeleaf to read
            long paidCount    = 0, partialCount = 0, pendingCount = 0;
            BigDecimal paidAmt = BigDecimal.ZERO,
                       partialAmt = BigDecimal.ZERO,
                       pendingAmt = BigDecimal.ZERO;

            for (Map<String, Object> row : breakdown) {
                String status = (String) row.get("payment_status");
                long   cnt    = ((Number) row
                    .get("bill_count")).longValue();
                BigDecimal amt = new BigDecimal(
                    row.get("total_amount").toString());

                if ("PAID".equals(status)) {
                    paidCount = cnt;
                    paidAmt   = amt;
                } else if ("PARTIAL".equals(status)) {
                    partialCount = cnt;
                    partialAmt   = amt;
                } else if ("PENDING".equals(status)) {
                    pendingCount = cnt;
                    pendingAmt   = amt;
                }
            }

            // Calculate percentage widths for the visual bar
            // These go into Thymeleaf as plain integers (0–100)
            int paidPct = totalBills > 0
                ? (int) Math.round(paidCount * 100.0 / totalBills) : 0;
            int partialPct = totalBills > 0
                ? (int) Math.round(partialCount * 100.0 / totalBills) : 0;
            int pendingPct = totalBills > 0
                ? 100 - paidPct - partialPct : 0;

            model.addAttribute("paidCount",    paidCount);
            model.addAttribute("partialCount", partialCount);
            model.addAttribute("pendingCount", pendingCount);
            model.addAttribute("paidAmt",      paidAmt);
            model.addAttribute("partialAmt",   partialAmt);
            model.addAttribute("pendingAmt",   pendingAmt);
            model.addAttribute("paidPct",      paidPct);
            model.addAttribute("partialPct",   partialPct);
            model.addAttribute("pendingPct",   pendingPct);

        } catch (Exception e) {
            // Safe defaults — all zeros
            model.addAttribute("paidCount",    0L);
            model.addAttribute("partialCount", 0L);
            model.addAttribute("pendingCount", 0L);
            model.addAttribute("paidPct",      0);
            model.addAttribute("partialPct",   0);
            model.addAttribute("pendingPct",   0);
        }

        // ── MONTHLY REVENUE ──────────────────────────────────
        try {
            model.addAttribute("monthlyRevenue",
                billService.getMonthlyRevenue());
        } catch (Exception e) {
            model.addAttribute("monthlyRevenue",
                Collections.emptyList());
        }

        // ── TOP CUSTOMERS ────────────────────────────────────
        try {
            model.addAttribute("topCustomers",
                billService.getTopCustomers());
        } catch (Exception e) {
            model.addAttribute("topCustomers",
                Collections.emptyList());
        }

        // ── PENDING REMINDERS ─────────────────────────────────
        try {
            model.addAttribute("pendingReminders",
                reminderService.getPendingReminders());
        } catch (Exception e) {
            model.addAttribute("pendingReminders",
                Collections.emptyList());
        }

        return "dashboard";
    }
}
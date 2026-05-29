package com.garage.billing.service;

import com.garage.billing.model.Bill;
import com.garage.billing.model.BillServiceItem;
import com.garage.billing.repository.BillRepository;
import com.garage.billing.util.BillNumberGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BillService {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillNumberGenerator billNumberGenerator;

    // ─────────────────────────────────────────────────────────
    // SAVE BILL WITH SERVICES
    // ─────────────────────────────────────────────────────────
    @Transactional
    public Bill saveBillWithServices(
            Bill bill,
            List<BillServiceItem> services
    ) {

        // Calculate total
        BigDecimal total = services.stream()
                .map(BillServiceItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Set totals
        bill.setTotalAmount(total);

        bill.setBalanceAmount(
                total.subtract(
                        bill.getPaidAmount() != null
                                ? bill.getPaidAmount()
                                : BigDecimal.ZERO
                )
        );

        // Set payment status
        bill.setPaymentStatus(
                determineStatus(
                        bill.getTotalAmount(),
                        bill.getPaidAmount()
                )
        );

        // Generate bill number
        bill.setBillNumber(
                billNumberGenerator.generate()
        );

        // Save bill
        Long billId = billRepository.saveBill(bill);

        bill.setId(billId);

        // Save services
        for (int i = 0; i < services.size(); i++) {

            BillServiceItem item = services.get(i);

            item.setBillId(billId);

            item.setSortOrder(i + 1);

            billRepository.saveService(item);
        }

        return bill;
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE BILL WITH SERVICES
    // ─────────────────────────────────────────────────────────
    @Transactional
    public Bill updateBillWithServices(
            Bill bill,
            List<BillServiceItem> newServices) {

        // Step 1: Calculate new total from edited services
        BigDecimal newTotal = newServices.stream()
                .map(BillServiceItem::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Step 2: Keep current paid amount
        BigDecimal currentPaid = bill.getPaidAmount() != null
                ? bill.getPaidAmount()
                : BigDecimal.ZERO;

        // Step 3: Recalculate balance
        BigDecimal newBalance = newTotal.subtract(currentPaid);

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }

        // Step 4: Determine payment status
        String newStatus = determineStatus(newTotal, currentPaid);

        // Step 5: Update bill object
        bill.setTotalAmount(newTotal);
        bill.setBalanceAmount(newBalance);
        bill.setPaymentStatus(newStatus);

        // Step 6: Update bill in DB
        billRepository.updateBill(bill);

        // Step 7: Delete old services
        billRepository.deleteServicesByBillId(bill.getId());

        // Step 8: Save new services
        for (int i = 0; i < newServices.size(); i++) {

            BillServiceItem item = newServices.get(i);

            item.setBillId(bill.getId());

            item.setSortOrder(i + 1);

            billRepository.saveService(item);
        }

        return bill;
    }

    // ─────────────────────────────────────────────────────────
    // DETERMINE PAYMENT STATUS
    // ─────────────────────────────────────────────────────────
    public String determineStatus(
            BigDecimal total,
            BigDecimal paid
    ) {

        if (paid == null ||
                paid.compareTo(BigDecimal.ZERO) == 0) {

            return "PENDING";
        }

        if (paid.compareTo(total) >= 0) {

            return "PAID";
        }

        return "PARTIAL";
    }

    // ─────────────────────────────────────────────────────────
    // FIND BILL BY ID
    // ─────────────────────────────────────────────────────────
    public Optional<Bill> findById(Long id) {

        Optional<Bill> bill =
                billRepository.findById(id);

        bill.ifPresent(b -> {

            List<BillServiceItem> services =
                    billRepository.findServicesByBillId(b.getId());

            b.setServices(services);
        });

        return bill;
    }

    // ─────────────────────────────────────────────────────────
    // GET CAR HISTORY
    // ─────────────────────────────────────────────────────────
    public List<Bill> getCarHistory(String carNumber) {

        List<Bill> bills =
                billRepository.findByCarNumber(carNumber);

        // Load services for each bill
        for (Bill b : bills) {

            b.setServices(
                    billRepository.findServicesByBillId(b.getId())
            );
        }

        return bills;
    }

    // ─────────────────────────────────────────────────────────
    // RECENT BILLS
    // ─────────────────────────────────────────────────────────
    public List<Bill> getRecentBills(int limit) {

        return billRepository.findRecent(limit);
    }

    // ─────────────────────────────────────────────────────────
    // GET BILLS BY DATE RANGE
    // ─────────────────────────────────────────────────────────
    public List<Bill> getBillsByDateRange(
            LocalDate fromDate,
            LocalDate toDate) {

        return billRepository.findByDateRange(
                fromDate,
                toDate
        );
    }

    // ─────────────────────────────────────────────────────────
    // OVERDUE BILLS
    // ─────────────────────────────────────────────────────────
    public List<Bill> getOverdueBills() {

        return billRepository.findOverdue();
    }

    // ─────────────────────────────────────────────────────────
    // DASHBOARD STATS
    // ─────────────────────────────────────────────────────────
    public BillRepository.DashboardStats getDashboardStats() {

        return billRepository.getDashboardStats();
    }

    // ─────────────────────────────────────────────────────────
    // OVERDUE COUNT
    // ─────────────────────────────────────────────────────────
    public long getOverdueCount() {

        return billRepository.countOverdue();
    }

    // ─────────────────────────────────────────────────────────
    // PAYMENT STATUS BREAKDOWN
    // ─────────────────────────────────────────────────────────
    public List<Map<String, Object>> getPaymentStatusBreakdown() {

        return billRepository.getPaymentStatusBreakdown();
    }

    // ─────────────────────────────────────────────────────────
    // MONTHLY REVENUE
    // ─────────────────────────────────────────────────────────
    public List<Map<String, Object>> getMonthlyRevenue() {

        return billRepository.getMonthlyRevenueCurrentYear();
    }

    // ─────────────────────────────────────────────────────────
    // TOP CUSTOMERS
    // ─────────────────────────────────────────────────────────
    public List<Map<String, Object>> getTopCustomers() {

        return billRepository.getTopCustomers();
    }

    // ─────────────────────────────────────────────────────────
    // CAR LIFETIME SUMMARY
    // ─────────────────────────────────────────────────────────
    public Map<String, Object> getCarLifetimeSummary(String carNumber) {

        try {

            return billRepository.getCarLifetimeSummary(carNumber);

        } catch (org.springframework.dao.EmptyResultDataAccessException e) {

            // No bills found — return safe empty map
            Map<String, Object> empty = new HashMap<>();

            empty.put("total_bills", 0L);

            empty.put("total_earned", BigDecimal.ZERO);

            empty.put("total_paid", BigDecimal.ZERO);

            empty.put("total_pending", BigDecimal.ZERO);

            empty.put("last_visit_date", null);

            return empty;
        }
    }

    // ─────────────────────────────────────────────────────────
    // GET BILLS BY CUSTOMER ID
    // ─────────────────────────────────────────────────────────
    public List<Bill> getBillsByCustomerId(Long customerId) {

        List<Bill> bills =
                billRepository.findByCustomerId(customerId);

        // Load services for each bill
        for (Bill b : bills) {

            b.setServices(
                    billRepository.findServicesByBillId(b.getId())
            );
        }

        return bills;
    }
}
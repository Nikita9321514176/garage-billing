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

    // ─────────────────────────────────────────────────────────
    // AUTO_SETTLEMENT REMOVED ENTIRELY.
    //
    // Was: if balance <= ₹100 → force balance to ZERO → status = PAID
    // Why it was wrong: Customer owes ₹50? System marks PAID.
    //   Owner never knows ₹50 is still pending. Real financial loss.
    // Fix: Balance = exactly what customer owes. Always.
    // ─────────────────────────────────────────────────────────

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillNumberGenerator billNumberGenerator;

    // ─────────────────────────────────────────────────────────
    // SAVE NEW BILL WITH SERVICES
    // ─────────────────────────────────────────────────────────
    @Transactional
    public Bill saveBillWithServices(Bill bill, List<BillServiceItem> services) {

        // Step 1: Sum all service amounts
        BigDecimal subtotal = services.stream()
                .map(BillServiceItem::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Step 2: Apply discount
        BigDecimal discount = bill.getDiscountAmount() != null
                ? bill.getDiscountAmount() : BigDecimal.ZERO;

        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
            bill.setDiscountAmount(discount);
        }

        // Step 3: Final total = subtotal - discount
        BigDecimal finalTotal = subtotal.subtract(discount);
        bill.setTotalAmount(finalTotal);

        // Step 4: Balance = total - paid
        BigDecimal paid = bill.getPaidAmount() != null
                ? bill.getPaidAmount() : BigDecimal.ZERO;

        if (paid.compareTo(finalTotal) > 0) paid = finalTotal;

        BigDecimal balance = finalTotal.subtract(paid);
        if (balance.compareTo(BigDecimal.ZERO) < 0) balance = BigDecimal.ZERO;

        // NO auto-settlement. Balance is exact.
        bill.setBalanceAmount(balance);

        // Step 5: Status
        bill.setPaymentStatus(determineStatus(finalTotal, paid, balance));

        // Step 6: Bill number
        bill.setBillNumber(billNumberGenerator.generate());

        // Step 7: Save bill
        Long billId = billRepository.saveBill(bill);
        bill.setId(billId);

        // Step 8: Save services
        for (int i = 0; i < services.size(); i++) {
            BillServiceItem item = services.get(i);
            item.setBillId(billId);
            item.setSortOrder(i + 1);
            billRepository.saveService(item);
        }

        return bill;
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE BILL WITH SERVICES (called from edit form)
    //
    // IMPORTANT: This method NEVER touches paidAmount.
    // paidAmount comes from the payments table (managed by PaymentService).
    // We only update: totalAmount, balanceAmount, paymentStatus, services.
    //
    // bill.getPaidAmount() here = value loaded from DB by controller
    // before calling this method. It is the authoritative paid amount.
    // ─────────────────────────────────────────────────────────
    @Transactional
    public Bill updateBillWithServices(Bill bill, List<BillServiceItem> newServices) {

        // Step 1: New total from edited services (no discount on edit — discount fixed at creation)
        BigDecimal newTotal = newServices.stream()
                .map(BillServiceItem::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Step 2: Current paid = from DB (loaded by controller, NOT from form)
        BigDecimal currentPaid = bill.getPaidAmount() != null
                ? bill.getPaidAmount() : BigDecimal.ZERO;

        // Cap paid at new total
        if (currentPaid.compareTo(newTotal) > 0) currentPaid = newTotal;

        // Step 3: Balance = newTotal - currentPaid
        BigDecimal newBalance = newTotal.subtract(currentPaid);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) newBalance = BigDecimal.ZERO;

        // NO auto-settlement. Balance is exact.

        // Step 4: Status
        String newStatus = determineStatus(newTotal, currentPaid, newBalance);

        // Step 5: Update bill fields
        bill.setTotalAmount(newTotal);
        bill.setBalanceAmount(newBalance);
        bill.setPaymentStatus(newStatus);

        // Step 6: Update bills table
        billRepository.updateBill(bill);

        // Step 7: Replace services
        billRepository.deleteServicesByBillId(bill.getId());
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
    // Called by BillService and PaymentService both.
    //
    // PAID    = balance is zero (fully settled)
    // PENDING = nothing paid at all
    // PARTIAL = paid some, owes some
    // ─────────────────────────────────────────────────────────
    public String determineStatus(BigDecimal total, BigDecimal paid, BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) == 0)           return "PAID";
        if (paid == null || paid.compareTo(BigDecimal.ZERO) == 0) return "PENDING";
        return "PARTIAL";
    }

    // ─────────────────────────────────────────────────────────
    // FIND BILL BY ID (with services loaded)
    // ─────────────────────────────────────────────────────────
    public Optional<Bill> findById(Long id) {
        Optional<Bill> bill = billRepository.findById(id);
        bill.ifPresent(b -> b.setServices(billRepository.findServicesByBillId(b.getId())));
        return bill;
    }

    public List<Bill> getCarHistory(String carNumber) {
        List<Bill> bills = billRepository.findByCarNumber(carNumber);
        for (Bill b : bills) b.setServices(billRepository.findServicesByBillId(b.getId()));
        return bills;
    }

    public List<Bill> getRecentBills(int limit) {
        return billRepository.findRecent(limit);
    }

    public List<Bill> getBillsByDateRange(LocalDate fromDate, LocalDate toDate) {
        return billRepository.findByDateRange(fromDate, toDate);
    }

    public List<Bill> getOverdueBills() {
        return billRepository.findOverdue();
    }

    public BillRepository.DashboardStats getDashboardStats() {
        return billRepository.getDashboardStats();
    }

    public long getOverdueCount() {
        return billRepository.countOverdue();
    }

    public List<Map<String, Object>> getPaymentStatusBreakdown() {
        return billRepository.getPaymentStatusBreakdown();
    }

    public List<Map<String, Object>> getMonthlyRevenue() {
        return billRepository.getMonthlyRevenueCurrentYear();
    }

    public List<Map<String, Object>> getTopCustomers() {
        return billRepository.getTopCustomers();
    }

    public Map<String, Object> getCarLifetimeSummary(String carNumber) {
        try {
            return billRepository.getCarLifetimeSummary(carNumber);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("total_bills",     0L);
            empty.put("total_earned",    BigDecimal.ZERO);
            empty.put("total_paid",      BigDecimal.ZERO);
            empty.put("total_pending",   BigDecimal.ZERO);
            empty.put("last_visit_date", null);
            return empty;
        }
    }

    public List<Bill> getBillsByCustomerId(Long customerId) {
        List<Bill> bills = billRepository.findByCustomerId(customerId);
        for (Bill b : bills) b.setServices(billRepository.findServicesByBillId(b.getId()));
        return bills;
    }
}

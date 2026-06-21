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

    @Autowired private BillRepository      billRepository;
    @Autowired private BillNumberGenerator billNumberGenerator;

    @Transactional
    public Bill saveBillWithServices(Bill bill, List<BillServiceItem> services) {

        BigDecimal subtotal = services.stream()
                .map(BillServiceItem::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = bill.getDiscountAmount() != null
                ? bill.getDiscountAmount() : BigDecimal.ZERO;
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
            bill.setDiscountAmount(discount);
        }

        BigDecimal finalTotal = subtotal.subtract(discount);
        bill.setTotalAmount(finalTotal);

        BigDecimal paid = bill.getPaidAmount() != null
                ? bill.getPaidAmount() : BigDecimal.ZERO;
        if (paid.compareTo(finalTotal) > 0) paid = finalTotal;

        BigDecimal balance = finalTotal.subtract(paid);
        if (balance.compareTo(BigDecimal.ZERO) < 0) balance = BigDecimal.ZERO;

        bill.setBalanceAmount(balance);
        bill.setPaymentStatus(determineStatus(finalTotal, paid, balance));
        bill.setBillNumber(billNumberGenerator.generate());

        Long billId = billRepository.saveBill(bill);
        bill.setId(billId);

        for (int i = 0; i < services.size(); i++) {
            BillServiceItem item = services.get(i);
            item.setBillId(billId);
            item.setSortOrder(i + 1);
            billRepository.saveService(item);
        }

        return bill;
    }

    @Transactional
    public Bill updateBillWithServices(Bill bill, List<BillServiceItem> newServices) {

        BigDecimal subtotal = newServices.stream()
                .map(BillServiceItem::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = bill.getDiscountAmount() != null
                ? bill.getDiscountAmount() : BigDecimal.ZERO;
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
            bill.setDiscountAmount(discount);
        }

        BigDecimal newTotal = subtotal.subtract(discount);

        BigDecimal currentPaid = bill.getPaidAmount() != null
                ? bill.getPaidAmount() : BigDecimal.ZERO;
        if (currentPaid.compareTo(newTotal) > 0) currentPaid = newTotal;

        BigDecimal newBalance = newTotal.subtract(currentPaid);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) newBalance = BigDecimal.ZERO;

        String newStatus = determineStatus(newTotal, currentPaid, newBalance);

        bill.setTotalAmount(newTotal);
        bill.setBalanceAmount(newBalance);
        bill.setPaymentStatus(newStatus);

        billRepository.updateBill(bill);

        billRepository.deleteServicesByBillId(bill.getId());
        for (int i = 0; i < newServices.size(); i++) {
            BillServiceItem item = newServices.get(i);
            item.setBillId(bill.getId());
            item.setSortOrder(i + 1);
            billRepository.saveService(item);
        }

        return bill;
    }

    public String determineStatus(BigDecimal total, BigDecimal paid, BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) == 0)              return "PAID";
        if (paid == null || paid.compareTo(BigDecimal.ZERO) == 0) return "PENDING";
        return "PARTIAL";
    }

    public Optional<Bill> findById(Long id) {
        Optional<Bill> bill = billRepository.findById(id);
        bill.ifPresent(b -> b.setServices(billRepository.findServicesByBillId(b.getId())));
        return bill;
    }

    // ── NEW: Find by bill number (for public invoice URL) ────
    public Optional<Bill> findByBillNumber(String billNumber) {
        Optional<Bill> bill = billRepository.findByBillNumber(billNumber);
        bill.ifPresent(b -> b.setServices(billRepository.findServicesByBillId(b.getId())));
        return bill;
    }

    public List<Bill> getCarHistory(String carNumber) {
        List<Bill> bills = billRepository.findByCarNumber(carNumber);
        for (Bill b : bills) b.setServices(billRepository.findServicesByBillId(b.getId()));
        return bills;
    }

    public List<Bill> getRecentBills(int limit)                              { return billRepository.findRecent(limit); }
    public List<Bill> getBillsByDateRange(LocalDate from, LocalDate to)      { return billRepository.findByDateRange(from, to); }
    public List<Bill> getOverdueBills()                                      { return billRepository.findOverdue(); }
    public BillRepository.DashboardStats getDashboardStats()                 { return billRepository.getDashboardStats(); }
    public long getOverdueCount()                                            { return billRepository.countOverdue(); }
    public List<Map<String, Object>> getPaymentStatusBreakdown()             { return billRepository.getPaymentStatusBreakdown(); }
    public List<Map<String, Object>> getMonthlyRevenue()                     { return billRepository.getMonthlyRevenueCurrentYear(); }
    public List<Map<String, Object>> getTopCustomers()                       { return billRepository.getTopCustomers(); }

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
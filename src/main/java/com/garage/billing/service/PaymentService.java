package com.garage.billing.service;

import com.garage.billing.model.Bill;
import com.garage.billing.model.Payment;
import com.garage.billing.repository.BillRepository;
import com.garage.billing.repository.PaymentRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private BillRepository    billRepository;
    @Autowired private BillService       billService;

    // ─────────────────────────────────────────────────────────
    // RECORD PAYMENT
    //
    // This is the ONLY method that should ever update
    // bills.paid_amount. It uses SUM from payments table
    // so the two tables are always in sync.
    //
    // AUTO_SETTLEMENT REMOVED:
    //   OLD: if (balance <= ₹100) → balance = ZERO → PAID
    //   That caused: customer owes ₹100 → shows PAID → owner
    //   never collects it. Real money lost.
    //   REMOVED. Balance is now exactly what customer owes.
    // ─────────────────────────────────────────────────────────
    @Transactional
    public void recordPayment(Payment payment) {

        // Step 1: Save new payment row
        paymentRepository.save(payment);

        // Step 2: Recalculate total paid = SUM of ALL payments for this bill
        // Using DB SUM, not in-memory increment → always correct
        BigDecimal totalPaid = paymentRepository.getTotalPaidForBill(payment.getBillId());

        // Step 3: Load bill for its totalAmount
        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() -> new RuntimeException(
                        "Bill not found: " + payment.getBillId()));

        // Step 4: Cap totalPaid at totalAmount (cannot overpay)
        if (totalPaid.compareTo(bill.getTotalAmount()) > 0) {
            totalPaid = bill.getTotalAmount();
        }

        // Step 5: Balance = totalAmount - totalPaid
        BigDecimal newBalance = bill.getTotalAmount().subtract(totalPaid);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) newBalance = BigDecimal.ZERO;

        // NO auto-settlement. ₹1 owed = ₹1 balance. Always exact.

        // Step 6: Status
        String newStatus = billService.determineStatus(
                bill.getTotalAmount(), totalPaid, newBalance);

        // Step 7: Update bills table
        billRepository.updatePaymentInfo(
                payment.getBillId(), totalPaid, newBalance, newStatus);
    }

    // ─────────────────────────────────────────────────────────
    // PAYMENT HISTORY FOR A BILL
    // ─────────────────────────────────────────────────────────
    public List<Payment> getPaymentHistory(Long billId) {
        return paymentRepository.findByBillId(billId);
    }

    public List<Map<String, Object>> getAllBillsWithPaymentStatus(String statusFilter) {
        return paymentRepository.getAllBillsWithPaymentStatus(statusFilter);
    }

    public Map<String, Object> getPaymentTrackingStats() {
        return paymentRepository.getPaymentTrackingStats();
    }

    public BigDecimal getCollectedToday() {
        return paymentRepository.getCollectedToday();
    }
}

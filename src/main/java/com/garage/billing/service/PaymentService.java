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

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillService billService;

    // ─────────────────────────────────────────────────────────
    // RECORD PAYMENT
    // ─────────────────────────────────────────────────────────
    // Saves payment and recalculates bill balance/status
    @Transactional
    public void recordPayment(Payment payment) {

        // Step 1: Save payment
        paymentRepository.save(payment);

        // Step 2: Calculate total paid so far
        BigDecimal totalPaid =
                paymentRepository.getTotalPaidForBill(
                        payment.getBillId()
                );

        // Step 3: Load bill
        Bill bill = billRepository.findById(payment.getBillId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Bill not found: " + payment.getBillId()
                        )
                );

        // Step 4: Recalculate balance
        BigDecimal newBalance =
                bill.getTotalAmount().subtract(totalPaid);

        // Prevent negative balance
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {

            newBalance = BigDecimal.ZERO;
        }

        // Step 5: Determine updated payment status
        String newStatus =
                billService.determineStatus(
                        bill.getTotalAmount(),
                        totalPaid
                );

        // Step 6: Update bill payment info
        billRepository.updatePaymentInfo(
                payment.getBillId(),
                totalPaid,
                newBalance,
                newStatus
        );
    }

    // ─────────────────────────────────────────────────────────
    // PAYMENT HISTORY
    // ─────────────────────────────────────────────────────────
    public List<Payment> getPaymentHistory(Long billId) {

        return paymentRepository.findByBillId(billId);
    }

    // ─────────────────────────────────────────────────────────
    // ALL BILLS WITH PAYMENT STATUS
    // ─────────────────────────────────────────────────────────
    public List<Map<String, Object>> getAllBillsWithPaymentStatus(
            String statusFilter
    ) {

        return paymentRepository
                .getAllBillsWithPaymentStatus(statusFilter);
    }

    // ─────────────────────────────────────────────────────────
    // PAYMENT TRACKING STATS
    // ─────────────────────────────────────────────────────────
    public Map<String, Object> getPaymentTrackingStats() {

        return paymentRepository.getPaymentTrackingStats();
    }

    // ─────────────────────────────────────────────────────────
    // COLLECTED TODAY
    // ─────────────────────────────────────────────────────────
    public BigDecimal getCollectedToday() {

        return paymentRepository.getCollectedToday();
    }
}
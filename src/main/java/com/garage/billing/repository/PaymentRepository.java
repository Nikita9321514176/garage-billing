package com.garage.billing.repository;

import com.garage.billing.model.Payment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PaymentRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<Payment> PAYMENT_MAPPER = (rs, rowNum) -> {

        Payment p = new Payment();

        p.setId(rs.getLong("id"));
        p.setBillId(rs.getLong("bill_id"));

        p.setAmountPaid(rs.getBigDecimal("amount_paid"));

        p.setPaymentMode(rs.getString("payment_mode"));

        p.setTransactionReference(
                rs.getString("transaction_reference")
        );

        p.setNotes(rs.getString("notes"));

        Timestamp ts = rs.getTimestamp("payment_date");

        if (ts != null) {
            p.setPaymentDate(ts.toLocalDateTime());
        }

        try {
            p.setBillNumber(rs.getString("bill_number"));
        } catch (Exception ignored) {
        }

        return p;
    };

    // ─────────────────────────────────────────────────────────
    // SAVE PAYMENT
    // ─────────────────────────────────────────────────────────
    public Long save(Payment payment) {

        String sql = """
            INSERT INTO payments
                (
                    bill_id,
                    amount_paid,
                    payment_mode,
                    transaction_reference,
                    notes
                )
            VALUES (?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(conn -> {

            PreparedStatement ps =
                    conn.prepareStatement(
                            sql,
                            Statement.RETURN_GENERATED_KEYS
                    );

            ps.setLong(1, payment.getBillId());

            ps.setBigDecimal(
                    2,
                    payment.getAmountPaid()
            );

            ps.setString(
                    3,
                    payment.getPaymentMode()
            );

            ps.setString(
                    4,
                    payment.getTransactionReference()
            );

            ps.setString(
                    5,
                    payment.getNotes()
            );

            return ps;

        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    // ─────────────────────────────────────────────────────────
    // FIND PAYMENTS BY BILL ID
    // ─────────────────────────────────────────────────────────
    public List<Payment> findByBillId(Long billId) {

        String sql = """
            SELECT p.*, b.bill_number
            FROM payments p
            JOIN bills b ON p.bill_id = b.id
            WHERE p.bill_id = ?
            ORDER BY p.payment_date DESC
            """;

        return jdbcTemplate.query(
                sql,
                PAYMENT_MAPPER,
                billId
        );
    }

    // ─────────────────────────────────────────────────────────
    // TOTAL PAID FOR BILL
    // ─────────────────────────────────────────────────────────
    public BigDecimal getTotalPaidForBill(Long billId) {

        String sql = """
            SELECT COALESCE(SUM(amount_paid), 0)
            FROM payments
            WHERE bill_id = ?
            """;

        BigDecimal total =
                jdbcTemplate.queryForObject(
                        sql,
                        BigDecimal.class,
                        billId
                );

        return total != null
                ? total
                : BigDecimal.ZERO;
    }

    // ─────────────────────────────────────────────────────────
    // ALL BILLS WITH PAYMENT STATUS
    // ─────────────────────────────────────────────────────────
    public List<Map<String, Object>> getAllBillsWithPaymentStatus(
            String statusFilter
    ) {

        StringBuilder sql = new StringBuilder("""
            SELECT
                b.id,
                b.bill_number,
                b.total_amount,
                b.paid_amount,
                b.balance_amount,
                b.payment_status,
                b.bill_date,
                b.due_date,
                cu.name  AS customer_name,
                cu.phone AS customer_phone,
                c.car_number,
                c.car_model,
                CASE
                    WHEN b.payment_status != 'PAID'
                      AND b.due_date IS NOT NULL
                      AND b.due_date < CURDATE()
                    THEN 1 ELSE 0
                END AS is_overdue,
                DATEDIFF(CURDATE(), b.due_date) AS days_overdue
            FROM bills b
            JOIN customers cu ON b.customer_id = cu.id
            JOIN cars      c  ON b.car_id      = c.id
            """);

        // Apply optional filter
        if (statusFilter != null
                && !statusFilter.trim().isEmpty()
                && !statusFilter.equalsIgnoreCase("ALL")) {

            if (statusFilter.equalsIgnoreCase("OVERDUE")) {

                sql.append("""
                    WHERE b.payment_status IN ('PENDING','PARTIAL')
                      AND b.due_date IS NOT NULL
                      AND b.due_date < CURDATE()
                    """);

            } else {

                sql.append("WHERE b.payment_status = '")
                        .append(statusFilter.toUpperCase())
                        .append("' ");
            }
        }

        sql.append("ORDER BY b.bill_date DESC");

        return jdbcTemplate.queryForList(sql.toString());
    }

    // ─────────────────────────────────────────────────────────
    // PAYMENT TRACKING STATS
    // ─────────────────────────────────────────────────────────
    public Map<String, Object> getPaymentTrackingStats() {

        String sql = """
            SELECT
                COUNT(
                    CASE
                        WHEN payment_status IN ('PENDING','PARTIAL')
                         AND due_date IS NOT NULL
                         AND due_date < CURDATE()
                        THEN 1
                    END
                ) AS overdue_count,

                COALESCE(
                    SUM(
                        CASE
                            WHEN payment_status != 'PAID'
                            THEN balance_amount
                        END
                    ),
                    0
                ) AS total_pending,

                COALESCE(
                    SUM(
                        CASE
                            WHEN payment_status = 'PARTIAL'
                            THEN 1
                        END
                    ),
                    0
                ) AS partial_count,

                COALESCE(
                    SUM(
                        CASE
                            WHEN payment_status = 'PENDING'
                            THEN 1
                        END
                    ),
                    0
                ) AS pending_count,

                COALESCE(
                    SUM(
                        CASE
                            WHEN payment_status = 'PAID'
                            THEN 1
                        END
                    ),
                    0
                ) AS paid_count

            FROM bills
            """;

        try {

            return jdbcTemplate.queryForMap(sql);

        } catch (Exception e) {

            return new HashMap<>();
        }
    }

    // ─────────────────────────────────────────────────────────
    // COLLECTED TODAY
    // ─────────────────────────────────────────────────────────
    public BigDecimal getCollectedToday() {

        String sql = """
            SELECT COALESCE(SUM(amount_paid), 0)
            FROM payments
            WHERE DATE(payment_date) = CURDATE()
            """;

        BigDecimal result =
                jdbcTemplate.queryForObject(
                        sql,
                        BigDecimal.class
                );

        return result != null
                ? result
                : BigDecimal.ZERO;
    }
}
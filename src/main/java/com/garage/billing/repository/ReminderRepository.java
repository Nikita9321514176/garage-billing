package com.garage.billing.repository;

import com.garage.billing.model.Reminder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Repository
public class ReminderRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── ROW MAPPER ──────────────────────────────────────────
    private static final RowMapper<Reminder> REMINDER_MAPPER =
            (rs, rowNum) -> {

        Reminder r = new Reminder();

        r.setId(rs.getLong("id"));
        r.setBillId(rs.getLong("bill_id"));
        r.setStatus(rs.getString("status"));
        r.setMessage(rs.getString("message"));

        Timestamp rd = rs.getTimestamp("reminder_date");
        if (rd != null) {
            r.setReminderDate(rd.toLocalDateTime());
        }

        Timestamp aa = rs.getTimestamp("actioned_at");
        if (aa != null) {
            r.setActionedAt(aa.toLocalDateTime());
        }

        // ── JOIN FIELDS ─────────────────────────────────────
        try {
            r.setBillNumber(rs.getString("bill_number"));
        } catch (Exception ignored) {}

        try {
            r.setCustomerName(rs.getString("customer_name"));
        } catch (Exception ignored) {}

        try {
            r.setCustomerPhone(rs.getString("customer_phone"));
        } catch (Exception ignored) {}

        try {
            r.setCarNumber(rs.getString("car_number"));
        } catch (Exception ignored) {}

        try {
            r.setBalanceAmount(rs.getBigDecimal("balance_amount"));
        } catch (Exception ignored) {}

        try {
            r.setDaysOverdue(rs.getInt("days_overdue"));
        } catch (Exception ignored) {}

        return r;
    };

    // ── SAVE ────────────────────────────────────────────────
    public Long save(Reminder reminder) {

        String sql = """
            INSERT INTO reminders (bill_id, status, message)
            VALUES (?, ?, ?)
            """;

        jdbcTemplate.update(
            sql,
            reminder.getBillId(),
            reminder.getStatus(),
            reminder.getMessage()
        );

        Long id = jdbcTemplate.queryForObject(
            "SELECT LAST_INSERT_ID()",
            Long.class
        );

        return id != null ? id : 0L;
    }

    // ── FIND PENDING WITH FULL DETAILS ──────────────────────
    // Uses LEFT JOIN so broken/deleted relations
    // do NOT silently remove reminders.
    public List<Reminder> findPendingWithDetails() {

        String sql = """
            SELECT
                r.*,
                b.bill_number,
                b.total_amount,
                b.balance_amount,
                b.due_date,
                COALESCE(
                    DATEDIFF(CURDATE(), b.due_date), 0
                ) AS days_overdue,
                cu.name  AS customer_name,
                cu.phone AS customer_phone,
                c.car_number
            FROM reminders r
            LEFT JOIN bills     b  ON r.bill_id     = b.id
            LEFT JOIN customers cu ON b.customer_id = cu.id
            LEFT JOIN cars      c  ON b.car_id      = c.id
            WHERE r.status = 'PENDING'
            ORDER BY days_overdue DESC
            """;

        return jdbcTemplate.query(sql, REMINDER_MAPPER);
    }

    // ── FIND ALL WITH DETAILS ───────────────────────────────
    // Uses LEFT JOIN so history never disappears
    // even if related rows are deleted.
    public List<Reminder> findAllWithDetails() {

        String sql = """
            SELECT
                r.*,
                b.bill_number,
                b.total_amount,
                b.balance_amount,
                b.due_date,
                COALESCE(
                    DATEDIFF(CURDATE(), b.due_date), 0
                ) AS days_overdue,
                cu.name  AS customer_name,
                cu.phone AS customer_phone,
                c.car_number
            FROM reminders r
            LEFT JOIN bills     b  ON r.bill_id     = b.id
            LEFT JOIN customers cu ON b.customer_id = cu.id
            LEFT JOIN cars      c  ON b.car_id      = c.id
            ORDER BY r.reminder_date DESC
            """;

        return jdbcTemplate.query(sql, REMINDER_MAPPER);
    }

    // ── CHECK: reminder already exists for this bill ────────
    public boolean existsPendingForBill(Long billId) {

        String sql = """
            SELECT COUNT(*)
            FROM reminders
            WHERE bill_id = ?
              AND status = 'PENDING'
            """;

        Integer count = jdbcTemplate.queryForObject(
            sql,
            Integer.class,
            billId
        );

        return count != null && count > 0;
    }

    // ── UPDATE STATUS ───────────────────────────────────────
    // status: "SENT" or "IGNORED"
    public void updateStatus(Long id, String status) {

        String sql = """
            UPDATE reminders
            SET status = ?,
                actioned_at = NOW()
            WHERE id = ?
            """;

        jdbcTemplate.update(sql, status, id);
    }

    // ── STATS FOR DASHBOARD CARDS ──────────────────────────
    // Uses LEFT JOIN so orphan reminders
    // are still counted correctly.
    public Map<String, Object> getReminderStats() {

        String sql = """
            SELECT
                COUNT(
                    CASE WHEN r.status = 'PENDING'
                    THEN 1 END
                ) AS pending_count,

                COUNT(
                    CASE WHEN r.status = 'SENT'
                    AND r.actioned_at >= DATE_SUB(
                        NOW(),
                        INTERVAL 7 DAY
                    )
                    THEN 1 END
                ) AS sent_this_week,

                COALESCE(
                    SUM(
                        CASE WHEN r.status = 'PENDING'
                        THEN b.balance_amount END
                    ),
                    0
                ) AS total_pending_balance

            FROM reminders r
            LEFT JOIN bills b ON r.bill_id = b.id
            """;

        try {
            return jdbcTemplate.queryForMap(sql);
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }

    // ── DELETE REMINDER ─────────────────────────────────────
    public void deleteById(Long id) {

        jdbcTemplate.update(
            "DELETE FROM reminders WHERE id = ?",
            id
        );
    }
}
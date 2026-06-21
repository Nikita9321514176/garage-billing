package com.garage.billing.repository;

import com.garage.billing.model.Bill;
import com.garage.billing.model.BillServiceItem;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class BillRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ─────────────────────────────────────────────────────────
    // BILL ROW MAPPER
    // ─────────────────────────────────────────────────────────
    private static final RowMapper<Bill> BILL_MAPPER = (rs, rowNum) -> {

        Bill bill = new Bill();

        bill.setId(rs.getLong("id"));
        bill.setCarId(rs.getLong("car_id"));
        bill.setCustomerId(rs.getLong("customer_id"));
        bill.setBillNumber(rs.getString("bill_number"));

        bill.setTotalAmount(rs.getBigDecimal("total_amount"));

     // NEW: Discount Amount
     BigDecimal discount = rs.getBigDecimal("discount_amount");
     bill.setDiscountAmount(
             discount != null
                     ? discount
                     : BigDecimal.ZERO
     );

     bill.setPaidAmount(rs.getBigDecimal("paid_amount"));
     bill.setBalanceAmount(rs.getBigDecimal("balance_amount"));

        bill.setPaymentStatus(rs.getString("payment_status"));
        bill.setNotes(rs.getString("notes"));

        Timestamp billDate = rs.getTimestamp("bill_date");

        if (billDate != null) {
            bill.setBillDate(billDate.toLocalDateTime());
        }

        Date dueDate = rs.getDate("due_date");

        if (dueDate != null) {
            bill.setDueDate(dueDate.toLocalDate());
        }

        // JOIN fields
        try {
            bill.setCustomerName(rs.getString("customer_name"));
        } catch (Exception ignored) {
        }

        try {
            bill.setCustomerPhone(rs.getString("customer_phone"));
        } catch (Exception ignored) {
        }

        try {
            bill.setCarNumber(rs.getString("car_number"));
        } catch (Exception ignored) {
        }

        try {
            bill.setCarModel(rs.getString("car_model"));
        } catch (Exception ignored) {
        }

        return bill;
    };

    // ─────────────────────────────────────────────────────────
    // SERVICE ROW MAPPER
    // ─────────────────────────────────────────────────────────
    private static final RowMapper<BillServiceItem> SERVICE_MAPPER =
            (rs, rowNum) -> {
 
        BillServiceItem item = new BillServiceItem();
 
        item.setId(rs.getLong("id"));
        item.setBillId(rs.getLong("bill_id"));
 
        item.setServiceName(rs.getString("service_name"));
        item.setDescription(rs.getString("description"));
 
        item.setAmount(rs.getBigDecimal("amount"));
 
        // NEW: read item_type (PART or LABOUR), safe fallback
        try {
            String type = rs.getString("item_type");
            item.setItemType(type != null ? type : "LABOUR");
        } catch (Exception e) {
            item.setItemType("LABOUR");
        }
 
        item.setSortOrder(rs.getInt("sort_order"));
 
        return item;
    };
 // ─────────────────────────────────────────────────────────
 // SAVE BILL
 // ─────────────────────────────────────────────────────────
 public Long saveBill(Bill bill) {

     String sql = """
         INSERT INTO bills
         (
             car_id,
             customer_id,
             bill_number,
             total_amount,
             discount_amount,
             paid_amount,
             balance_amount,
             payment_status,
             due_date,
             notes
         )
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         """;

     KeyHolder keyHolder = new GeneratedKeyHolder();

     jdbcTemplate.update(conn -> {

         PreparedStatement ps =
                 conn.prepareStatement(
                         sql,
                         Statement.RETURN_GENERATED_KEYS
                 );

         ps.setLong(1, bill.getCarId());
         ps.setLong(2, bill.getCustomerId());

         ps.setString(3, bill.getBillNumber());

         ps.setBigDecimal(4, bill.getTotalAmount());

         // NEW: Discount Amount
         ps.setBigDecimal(
                 5,
                 bill.getDiscountAmount() != null
                         ? bill.getDiscountAmount()
                         : BigDecimal.ZERO
         );

         ps.setBigDecimal(6, bill.getPaidAmount());
         ps.setBigDecimal(7, bill.getBalanceAmount());

         ps.setString(8, bill.getPaymentStatus());

         ps.setObject(
                 9,
                 bill.getDueDate() != null
                         ? Date.valueOf(bill.getDueDate())
                         : null
         );

         ps.setString(10, bill.getNotes());

         return ps;

     }, keyHolder);

     return keyHolder.getKey().longValue();
 }

    // ─────────────────────────────────────────────────────────
    // SAVE SERVICE
    // ─────────────────────────────────────────────────────────
 public void saveService(BillServiceItem item) {
	 
     String sql = """
         INSERT INTO bill_services
         (
             bill_id,
             service_name,
             description,
             amount,
             item_type,
             sort_order
         )
         VALUES (?, ?, ?, ?, ?, ?)
         """;

     jdbcTemplate.update(
             sql,
             item.getBillId(),
             item.getServiceName(),
             item.getDescription(),
             item.getAmount(),
             // NEW: item_type — defaults to LABOUR if null
             item.getItemType() != null ? item.getItemType() : "LABOUR",
             item.getSortOrder()
     );
 }

    // ─────────────────────────────────────────────────────────
    // DELETE SERVICES BY BILL ID
    // ─────────────────────────────────────────────────────────
    public void deleteServicesByBillId(Long billId) {

        String sql = """
            DELETE FROM bill_services
            WHERE bill_id = ?
            """;

        jdbcTemplate.update(sql, billId);
    }

 // ─────────────────────────────────────────────────────────
 // UPDATE BILL
 // ─────────────────────────────────────────────────────────
 public void updateBill(Bill bill) {

     String sql = """
         UPDATE bills
         SET total_amount    = ?,
             discount_amount = ?,
             paid_amount     = ?,
             balance_amount  = ?,
             payment_status  = ?,
             due_date        = ?,
             notes           = ?
         WHERE id = ?
         """;

     jdbcTemplate.update(
             sql,

             bill.getTotalAmount(),

             bill.getDiscountAmount() != null
                     ? bill.getDiscountAmount()
                     : BigDecimal.ZERO,

             bill.getPaidAmount(),

             bill.getBalanceAmount(),

             bill.getPaymentStatus(),

             bill.getDueDate() != null
                     ? java.sql.Date.valueOf(bill.getDueDate())
                     : null,

             bill.getNotes(),

             bill.getId()
     );
 }

    // ─────────────────────────────────────────────────────────
    // FIND BILL BY ID
    // ─────────────────────────────────────────────────────────
    public Optional<Bill> findById(Long id) {

        String sql = """
            SELECT b.*,
                   cu.name  AS customer_name,
                   cu.phone AS customer_phone,
                   c.car_number,
                   c.car_model
            FROM bills b
            JOIN customers cu
                ON b.customer_id = cu.id
            JOIN cars c
                ON b.car_id = c.id
            WHERE b.id = ?
            """;

        List<Bill> results =
                jdbcTemplate.query(sql, BILL_MAPPER, id);

        return results.stream().findFirst();
    }
    
 // ─────────────────────────────────────────────────────────
 // FIND BILL BY BILL NUMBER
 // ─────────────────────────────────────────────────────────
 public Optional<Bill> findByBillNumber(String billNumber) {

     String sql = """
         SELECT b.*,
                cu.name  AS customer_name,
                cu.phone AS customer_phone,
                c.car_number,
                c.car_model
         FROM bills b
         JOIN customers cu ON b.customer_id = cu.id
         JOIN cars      c  ON b.car_id      = c.id
         WHERE b.bill_number = ?
         """;

     List<Bill> results =
             jdbcTemplate.query(sql, BILL_MAPPER, billNumber);

     return results.stream().findFirst();
 }

    // ─────────────────────────────────────────────────────────
    // FIND BY CAR NUMBER
    // ─────────────────────────────────────────────────────────
    public List<Bill> findByCarNumber(String carNumber) {

        String sql = """
            SELECT b.*,
                   cu.name  AS customer_name,
                   cu.phone AS customer_phone,
                   c.car_number,
                   c.car_model
            FROM bills b
            JOIN cars c
                ON b.car_id = c.id
            JOIN customers cu
                ON b.customer_id = cu.id
            WHERE c.car_number = ?
            ORDER BY b.bill_date DESC
            """;

        return jdbcTemplate.query(sql, BILL_MAPPER, carNumber);
    }

    // ─────────────────────────────────────────────────────────
    // FIND BY CUSTOMER ID
    // ─────────────────────────────────────────────────────────
    public List<Bill> findByCustomerId(Long customerId) {

        String sql = """
            SELECT b.*,
                   cu.name  AS customer_name,
                   cu.phone AS customer_phone,
                   c.car_number,
                   c.car_model
            FROM bills b
            JOIN customers cu ON b.customer_id = cu.id
            JOIN cars      c  ON b.car_id      = c.id
            WHERE b.customer_id = ?
            ORDER BY b.bill_date DESC
            """;

        return jdbcTemplate.query(sql, BILL_MAPPER, customerId);
    }

    // ─────────────────────────────────────────────────────────
    // FIND SERVICES FOR BILL
    // ─────────────────────────────────────────────────────────
    public List<BillServiceItem> findServicesByBillId(Long billId) {

        String sql = """
            SELECT *
            FROM bill_services
            WHERE bill_id = ?
            ORDER BY sort_order ASC
            """;

        return jdbcTemplate.query(sql, SERVICE_MAPPER, billId);
    }

    // ─────────────────────────────────────────────────────────
    // RECENT BILLS
    // ─────────────────────────────────────────────────────────
    public List<Bill> findRecent(int limit) {

        String sql = """
            SELECT b.*,
                   cu.name  AS customer_name,
                   cu.phone AS customer_phone,
                   c.car_number,
                   c.car_model
            FROM bills b
            JOIN customers cu
                ON b.customer_id = cu.id
            JOIN cars c
                ON b.car_id = c.id
            ORDER BY b.bill_date DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, BILL_MAPPER, limit);
    }

    // ─────────────────────────────────────────────────────────
    // FIND BILLS BY DATE RANGE
    // ─────────────────────────────────────────────────────────
    public List<Bill> findByDateRange(
            java.time.LocalDate fromDate,
            java.time.LocalDate toDate) {

        String sql = """
            SELECT b.*,
                   cu.name  AS customer_name,
                   cu.phone AS customer_phone,
                   c.car_number,
                   c.car_model
            FROM bills b
            JOIN customers cu ON b.customer_id = cu.id
            JOIN cars      c  ON b.car_id      = c.id
            WHERE DATE(b.bill_date) BETWEEN ? AND ?
            ORDER BY b.bill_date DESC
            """;

        return jdbcTemplate.query(
                sql,
                BILL_MAPPER,
                java.sql.Date.valueOf(fromDate),
                java.sql.Date.valueOf(toDate)
        );
    }

    // ─────────────────────────────────────────────────────────
    // OVERDUE BILLS
    // ─────────────────────────────────────────────────────────
    public List<Bill> findOverdue() {

        String sql = """
            SELECT b.*,
                   cu.name  AS customer_name,
                   cu.phone AS customer_phone,
                   c.car_number,
                   c.car_model
            FROM bills b
            JOIN customers cu
                ON b.customer_id = cu.id
            JOIN cars c
                ON b.car_id = c.id
            WHERE b.payment_status IN ('PENDING','PARTIAL')
              AND b.due_date < CURDATE()
            ORDER BY b.due_date ASC
            """;

        return jdbcTemplate.query(sql, BILL_MAPPER);
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE PAYMENT INFO
    // ─────────────────────────────────────────────────────────
    public void updatePaymentInfo(
            Long billId,
            BigDecimal paidAmount,
            BigDecimal balanceAmount,
            String status
    ) {

        String sql = """
            UPDATE bills
            SET paid_amount = ?,
                balance_amount = ?,
                payment_status = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(
                sql,
                paidAmount,
                balanceAmount,
                status,
                billId
        );
    }

    // ─────────────────────────────────────────────────────────
    // DASHBOARD STATS
    // ─────────────────────────────────────────────────────────
    public DashboardStats getDashboardStats() {

        String sql = """
            SELECT
                COUNT(DISTINCT id)           AS total_bills,
                COUNT(DISTINCT customer_id)  AS total_customers,
                COUNT(DISTINCT car_id)       AS total_cars,
                COALESCE(SUM(total_amount),0)   AS total_revenue,
                COALESCE(SUM(paid_amount),0)    AS total_collected,
                COALESCE(SUM(balance_amount),0) AS total_pending
            FROM bills
            """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {

            DashboardStats stats = new DashboardStats();

            stats.totalBills =
                    rs.getLong("total_bills");

            stats.totalCustomers =
                    rs.getLong("total_customers");

            stats.totalCars =
                    rs.getLong("total_cars");

            stats.totalRevenue =
                    rs.getBigDecimal("total_revenue");

            stats.totalCollected =
                    rs.getBigDecimal("total_collected");

            stats.totalPending =
                    rs.getBigDecimal("total_pending");

            return stats;
        });
    }

    // ─────────────────────────────────────────────────────────
    // COUNT OVERDUE BILLS
    // ─────────────────────────────────────────────────────────
    public long countOverdue() {

        String sql = """
            SELECT COUNT(*)
            FROM bills
            WHERE payment_status IN ('PENDING','PARTIAL')
              AND due_date IS NOT NULL
              AND due_date < CURDATE()
            """;

        Long count =
                jdbcTemplate.queryForObject(sql, Long.class);

        return count != null ? count : 0L;
    }

    // ─────────────────────────────────────────────────────────
    // PAYMENT STATUS BREAKDOWN
    // ─────────────────────────────────────────────────────────
    public List<Map<String, Object>> getPaymentStatusBreakdown() {

        String sql = """
            SELECT
                payment_status,
                COUNT(*)                        AS bill_count,
                COALESCE(SUM(total_amount), 0) AS total_amount
            FROM bills
            GROUP BY payment_status
            ORDER BY payment_status
            """;

        return jdbcTemplate.queryForList(sql);
    }

    // ─────────────────────────────────────────────────────────
    // MONTHLY REVENUE
    // ─────────────────────────────────────────────────────────
    public List<Map<String, Object>> getMonthlyRevenueCurrentYear() {

        String sql = """
            SELECT
                MONTH(bill_date)                   AS month_num,
                DATE_FORMAT(bill_date, '%b')       AS month_name,
                COALESCE(SUM(total_amount), 0)     AS revenue,
                COALESCE(SUM(paid_amount), 0)      AS collected,
                COALESCE(SUM(balance_amount), 0)   AS pending
            FROM bills
            WHERE YEAR(bill_date) = YEAR(CURDATE())
            GROUP BY MONTH(bill_date),
                     DATE_FORMAT(bill_date, '%b')
            ORDER BY month_num
            """;

        return jdbcTemplate.queryForList(sql);
    }

    // ─────────────────────────────────────────────────────────
    // TOP CUSTOMERS
    // ─────────────────────────────────────────────────────────
    public List<Map<String, Object>> getTopCustomers() {

        String sql = """
            SELECT
                cu.name                            AS customer_name,
                cu.phone                           AS phone,
                COUNT(b.id)                        AS bill_count,
                COALESCE(SUM(b.total_amount), 0)   AS total_spent,
                COALESCE(SUM(b.balance_amount), 0) AS total_pending
            FROM customers cu
            JOIN bills b
                ON cu.id = b.customer_id
            GROUP BY cu.id, cu.name, cu.phone
            ORDER BY total_spent DESC
            LIMIT 5
            """;

        return jdbcTemplate.queryForList(sql);
    }

    // ─────────────────────────────────────────────────────────
    // CAR LIFETIME SUMMARY
    // ─────────────────────────────────────────────────────────
    public Map<String, Object> getCarLifetimeSummary(String carNumber) {

        String sql = """
            SELECT
                COUNT(DISTINCT b.id)               AS total_bills,
                COALESCE(SUM(b.total_amount), 0)   AS total_earned,
                COALESCE(SUM(b.paid_amount), 0)    AS total_paid,
                COALESCE(SUM(b.balance_amount), 0) AS total_pending,
                MAX(b.bill_date)                   AS last_visit_date
            FROM bills b
            JOIN cars c
                ON b.car_id = c.id
            WHERE c.car_number = ?
            """;

        return jdbcTemplate.queryForMap(sql, carNumber);
    }

    // ─────────────────────────────────────────────────────────
    // DASHBOARD STATS CLASS
    // ─────────────────────────────────────────────────────────
    public static class DashboardStats {

        public long totalBills;
        public long totalCustomers;
        public long totalCars;

        public BigDecimal totalRevenue;
        public BigDecimal totalCollected;
        public BigDecimal totalPending;
    }
}
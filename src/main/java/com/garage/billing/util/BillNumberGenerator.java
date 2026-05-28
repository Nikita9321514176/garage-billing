package com.garage.billing.util;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

@Component
public class BillNumberGenerator {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Generate bill number like:
    // BILL-2026-001
    public String generate() {

        // Get current year
        int currentYear = LocalDate.now().getYear();

        // Count bills of current year
        String sql =
                "SELECT COUNT(*) FROM bills WHERE YEAR(bill_date) = ?";

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                currentYear
        );

        // Next bill number
        int nextNumber = (count == null ? 0 : count) + 1;

        // Return formatted bill number
        return String.format(
                "BILL-%d-%03d",
                currentYear,
                nextNumber
        );
    }
}
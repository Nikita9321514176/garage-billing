package com.garage.billing.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reminder {

    private Long id;

    private Long billId;

    private LocalDateTime reminderDate;

    private String status;

    private String message;

    private LocalDateTime actionedAt;

    // ─────────────────────────────────────────────────────────
    // JOIN / TRANSIENT DISPLAY FIELDS
    // ─────────────────────────────────────────────────────────

    private String billNumber;

    private String customerName;

    private String customerPhone;

    private String carNumber;

    private BigDecimal balanceAmount;

    // How many days past due_date this bill is.
    // Populated from SQL DATEDIFF — not stored in DB.
    private Integer daysOverdue;
}
package com.garage.billing.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillServiceItem {

    private Long id;

    private Long billId;

    private String serviceName;

    private String description;

    private BigDecimal amount;

    // ── NEW: itemType ─────────────────────────────────────────
    // "PART" or "LABOUR". Maps to bill_services.item_type
    // (ENUM column, default 'LABOUR', added via migration.sql).
    // Used by PdfService to split services into the Labour table
    // and Parts table required by the new invoice design.
    private String itemType;

    private Integer sortOrder;
}
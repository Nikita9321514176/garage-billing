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

    // "PART" or "LABOUR". Maps to bill_services.item_type.
    private String itemType;

    // ── NEW: qty ───────────────────────────────────────────────
    // Only meaningful when itemType = "PART". Maps to
    // bill_services.qty (nullable DECIMAL). Null for LABOUR rows.
    // Used to re-populate the edit form's Qty field correctly,
    // and to recompute amount = qty * unitPrice when qty/price
    // change during editing.
    private BigDecimal qty;

    // ── NEW: unitPrice ─────────────────────────────────────────
    // Only meaningful when itemType = "PART". Maps to
    // bill_services.unit_price (nullable DECIMAL). Null for
    // LABOUR rows. amount = qty * unitPrice for PART rows —
    // this is enforced in BillController when parsing the form.
    private BigDecimal unitPrice;

    private Integer sortOrder;
}
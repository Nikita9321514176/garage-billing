package com.garage.billing.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class BillForm {

    private Long existingCustomerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;

    private Long existingCarId;
    private String carNumber;
    private String carModel;
    private String carBrand;
    private Integer carManufactureYear;
    private String carColor;

    private BigDecimal initialPayment;
    private BigDecimal discountAmount;
    private String initialPaymentMode;
    private String initialPaymentReference;

    private String dueDate;
    private String notes;

    // ── LABOUR / SERVICE LINES ─────────────────────────────────
    // AutoPopulatingList — auto-grows for dynamic JS-added rows.
    // Every row here is treated as LABOUR (existing behavior,
    // unchanged from before Parts existed).
    private List<ServiceLine> services =
            new AutoPopulatingList<>(ServiceLine.class);

    // ── NEW: PART LINES ─────────────────────────────────────────
    // Separate list, separate form section. Each row here becomes
    // a BillServiceItem with itemType="PART" in the controller.
    // AutoPopulatingList for the same reason as services — the
    // edit page pre-populates existing PART rows, and JS-added
    // new rows must bind correctly at higher indices.
    private List<PartLine> parts =
            new AutoPopulatingList<>(PartLine.class);

    @Data
    @NoArgsConstructor
    public static class ServiceLine {

        private String serviceName;
        private String description;
        private BigDecimal amount;

        // Kept for backward compatibility with any code path that
        // still sets it explicitly. Controller now always treats
        // every row in `services` as LABOUR regardless of this value.
        private String itemType;
    }

    // ── NEW INNER CLASS: PartLine ────────────────────────────────
    // Represents ONE row in the Parts Used table on the bill form.
    // maps to: parts[i].partName / parts[i].qty / parts[i].unitPrice
    @Data
    @NoArgsConstructor
    public static class PartLine {

        // maps to: parts[i].partName
        private String partName;

        // maps to: parts[i].qty
        private BigDecimal qty;

        // maps to: parts[i].unitPrice
        private BigDecimal unitPrice;
    }
}
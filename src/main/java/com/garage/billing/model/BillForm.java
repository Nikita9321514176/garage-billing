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

    // AutoPopulatingList — auto-grows for dynamic JS-added rows.
    private List<ServiceLine> services =
            new AutoPopulatingList<>(ServiceLine.class);

    @Data
    @NoArgsConstructor
    public static class ServiceLine {

        private String serviceName;
        private String description;
        private BigDecimal amount;

        // ── NEW: itemType ──────────────────────────────────────
        // maps to: services[i].itemType
        // Bound from the PART/LABOUR dropdown on bill/create.html
        // and bill/edit.html. Controller defaults to "LABOUR" if
        // null/blank — never breaks existing form submissions.
        private String itemType;
    }
}
package com.garage.billing.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bill {

    private Long id;

    private Long carId;

    private Long customerId;

    private String billNumber;

    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;

    private String paymentStatus;

    private LocalDateTime billDate;

    private LocalDate dueDate;

    private String notes;

    private String customerName;
    private String customerPhone;
    private String carNumber;
    private String carModel;

    private List<BillServiceItem> services;
}
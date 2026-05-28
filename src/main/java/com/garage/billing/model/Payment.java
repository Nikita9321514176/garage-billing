package com.garage.billing.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    private Long id;

    private Long billId;

    private BigDecimal amountPaid;

    private String paymentMode;

    private LocalDateTime paymentDate;

    private String transactionReference;

    private String notes;

    private String billNumber;
}
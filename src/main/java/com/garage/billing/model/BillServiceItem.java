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

    private Integer sortOrder;
}
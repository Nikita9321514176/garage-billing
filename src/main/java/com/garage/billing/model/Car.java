package com.garage.billing.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Car {

    private Long id;

    private Long customerId;

    private String carNumber;

    private String carModel;

    private String brand;

    private Integer manufactureYear;

    private String color;

    private LocalDateTime createdAt;

    private String customerName;
}
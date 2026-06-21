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

    // ── NEW: engineNumber ──────────────────────────────────────
    // Maps to cars.engine_number (VARCHAR, nullable).
    // Displayed on invoice Vehicle Information section.
    // Optional field — existing cars will show "-" until filled.
    private String engineNumber;

    // ── NEW: registrationYear ──────────────────────────────────
    // Maps to cars.registration_year (INT, nullable).
    // "Year of registration" on the invoice — distinct from
    // manufactureYear (a car can be manufactured one year and
    // registered the next). Optional — shows "-" if not set.
    private Integer registrationYear;

    private LocalDateTime createdAt;

    private String customerName;
}
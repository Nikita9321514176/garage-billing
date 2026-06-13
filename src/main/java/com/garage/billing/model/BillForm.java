package com.garage.billing.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.util.List;

// BillForm is a DTO — Data Transfer Object.
// It is NOT stored in the database directly.
// Its only purpose: receive the HTML form POST data cleanly.
//
// Why a separate class instead of using Bill directly?
// Because the form sends things Bill doesn't have (like customerName as a string
// to create/find the customer), and Bill has things the form doesn't send
// (like billNumber which is auto-generated server-side).
// DTOs keep your model classes clean.
@Data
@NoArgsConstructor
public class BillForm {

    // ── CUSTOMER FIELDS ───────────────────────────────────────
    // Either the owner selects an existing customer (existingCustomerId)
    // OR they type in details for a new one.
    // The controller handles both cases.
    private Long existingCustomerId;      // set if existing customer selected
    private String customerName;          // set if new customer
    private String customerPhone;         // set if new customer
    private String customerEmail;         // optional for new customer

    // ── CAR FIELDS ────────────────────────────────────────────
    private Long existingCarId;           // set if car already registered
    private String carNumber;             // set if new car
    private String carModel;              // set if new car
    private String carBrand;              // optional

    // NEW CAR DETAILS
    private Integer carManufactureYear;
    private String carColor;

    // ── PAYMENT FIELDS ────────────────────────────────────────
    // How much the customer is paying RIGHT NOW (at time of bill creation).
    // Can be 0 (full credit), partial, or full amount.
    private BigDecimal initialPayment;

    // Discount amount in rupees entered during bill creation
    // Example: Total = 1000, Discount = 100, Final = 900
    private BigDecimal discountAmount;

    // Payment mode chosen at bill creation time
    // Options: CASH, UPI, CARD, CHEQUE, BANK_TRANSFER
    // null = no payment made (no payment record created)
    private String initialPaymentMode;

    // UPI ref, cheque number, card last 4 digits etc.
    private String initialPaymentReference;

    // ── DUE DATE ──────────────────────────────────────────────
    // Received as YYYY-MM-DD string from HTML date input
    private String dueDate;

    // ── NOTES ─────────────────────────────────────────────────
    private String notes;

    // ── SERVICE LINES ─────────────────────────────────────────
    // FIX: Replaced plain ArrayList with Spring's AutoPopulatingList.
    //
    // ROOT CAUSE OF BUG 2 (services merging):
    //   Plain ArrayList has a fixed size equal to however many items
    //   were pre-populated (e.g. 2 existing services from DB).
    //   When the edit form dynamically adds new rows via JavaScript,
    //   those rows submit as services[2], services[3], etc.
    //   Spring MVC calls list.set(2, value) on a size-2 list
    //   → IndexOutOfBoundsException → Spring silently drops those
    //   entries OR concatenates them into the previous field,
    //   producing "alignment,denting" as a single service name.
    //
    // FIX: AutoPopulatingList is a Spring-native class (spring-core,
    //   already in your classpath — no new dependency needed).
    //   It auto-grows to ANY index Spring tries to access.
    //   services[0], services[1], services[2], services[3] all bind
    //   correctly regardless of how many rows were pre-populated.
    private List<ServiceLine> services =
            new AutoPopulatingList<>(ServiceLine.class);

    // ── INNER CLASS: ServiceLine ──────────────────────────────
    // Represents ONE row in the services table on the bill form.
    // Must have a public no-arg constructor for AutoPopulatingList
    // to instantiate new ServiceLine objects when auto-growing.
    // @NoArgsConstructor on the outer class does NOT cover inner
    // static classes — the explicit no-arg constructor below is required.
    @Data
    @NoArgsConstructor
    public static class ServiceLine {

        // maps to: services[i].serviceName
        private String serviceName;

        // maps to: services[i].description
        private String description;

        // maps to: services[i].amount
        private BigDecimal amount;
    }
}
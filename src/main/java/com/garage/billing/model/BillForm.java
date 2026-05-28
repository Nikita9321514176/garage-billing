package com.garage.billing.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

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
    private Long   existingCustomerId;    // set if existing customer selected
    private String customerName;          // set if new customer
    private String customerPhone;         // set if new customer
    private String customerEmail;         // optional for new customer

    // ── CAR FIELDS ────────────────────────────────────────────
    private Long   existingCarId;         // set if car already registered
    private String carNumber;             // set if new car
    private String carModel;              // set if new car
    private String carBrand;              // optional

    // ── PAYMENT FIELDS ────────────────────────────────────────
    // How much the customer is paying RIGHT NOW (at time of bill creation).
    // Can be 0 (full credit), partial, or full amount.
    private BigDecimal initialPayment;

    // ── DUE DATE ──────────────────────────────────────────────
    // When remaining balance should be paid by.
    private String dueDate;               // received as "2024-05-25" string from date input

    // ── NOTES ─────────────────────────────────────────────────
    private String notes;

    // ── SERVICE LINES ─────────────────────────────────────────
    // This is the key field. When the form sends:
    //   services[0].serviceName=Oil Change
    //   services[0].amount=800
    //   services[1].serviceName=Tyre Rotation
    //   services[1].amount=300
    // Spring automatically populates this list with 2 ServiceLine objects.
    //
    // We initialize with an empty ArrayList to prevent NullPointerException
    // if the form somehow sends zero services.
    private List<ServiceLine> services = new ArrayList<>();

    // ── INNER CLASS: ServiceLine ───────────────────────────────
    // Represents ONE row in the services table on the bill form.
    // Inner class because it's only used inside BillForm.
    @Data
    @NoArgsConstructor
    public static class ServiceLine {
        // maps to: services[i].serviceName in the form
        private String     serviceName;
        // maps to: services[i].description
        private String     description;
        // maps to: services[i].amount
        private BigDecimal amount;
    }
}
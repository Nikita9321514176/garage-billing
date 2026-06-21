package com.garage.billing.controller;

import com.garage.billing.model.*;
import com.garage.billing.service.BillService;
import com.garage.billing.service.CustomerService;
import com.garage.billing.service.PaymentService;
import com.garage.billing.service.PdfService;
import com.garage.billing.repository.CarRepository;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/bill")
public class BillController {

    @Autowired private BillService    billService;
    @Autowired private CustomerService customerService;
    @Autowired private CarRepository  carRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private PdfService     pdfService;

    // ─────────────────────────────────────────────────────────
    // SHOW BILL CREATION PAGE — GET /bill/new
    // ─────────────────────────────────────────────────────────
    @GetMapping("/new")
    public String showNewBillForm(
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "carId",      required = false) Long carId,
            Model model) {

        BillForm billForm = new BillForm();
        if (customerId != null) billForm.setExistingCustomerId(customerId);
        if (carId      != null) billForm.setExistingCarId(carId);

        model.addAttribute("billForm", billForm);
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("cars", customerId != null
                ? customerService.getCarsForCustomer(customerId)
                : new ArrayList<>());

        return "bill/create";
    }

    // ─────────────────────────────────────────────────────────
    // SHOW EDIT FORM — GET /bill/{id}/edit
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model,
                               RedirectAttributes redirectAttributes) {

        Optional<Bill> billOpt = billService.findById(id);
        if (billOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bill not found: " + id);
            return "redirect:/bill/list";
        }

        Bill bill = billOpt.get();
        System.out.println("========== EDIT BILL ==========");
        System.out.println("Bill Number = " + bill.getBillNumber());

        if (bill.getServices() != null) {
            System.out.println("Services Count = " + bill.getServices().size());

            for (BillServiceItem item : bill.getServices()) {
                System.out.println(
                        item.getServiceName() + " = " + item.getAmount()
                );
            }
        }
        System.out.println("===============================");

        model.addAttribute("bill", bill);
        model.addAttribute("services", bill.getServices());

        // Pass discount amount to edit page JavaScript
        model.addAttribute(
                "discountAmount",
                bill.getDiscountAmount() != null
                        ? bill.getDiscountAmount()
                        : BigDecimal.ZERO
        );

        BillForm billForm = new BillForm();
        billForm.setNotes(bill.getNotes());

        if (bill.getDueDate() != null) {
            billForm.setDueDate(bill.getDueDate().toString());
        }

        List<BillForm.ServiceLine> lines = new ArrayList<>();

        if (bill.getServices() != null) {
            for (BillServiceItem item : bill.getServices()) {
                BillForm.ServiceLine line = new BillForm.ServiceLine();
                line.setServiceName(item.getServiceName());
                line.setDescription(item.getDescription());
                line.setAmount(item.getAmount());
                lines.add(line);
            }
        }

        billForm.setServices(lines);

        model.addAttribute("billForm", billForm);
        model.addAttribute("customers", customerService.findAll());

        return "bill/edit";
    }

    // ─────────────────────────────────────────────────────────
    // SAVE EDITED BILL — POST /bill/{id}/edit
    //
    // KEY FIX: We no longer do bill.setPaidAmount(form value).
    //
    // OLD (broken):
    //   bill.setPaidAmount(billForm.getInitialPayment())
    //   → directly overwrote bills.paid_amount with whatever owner typed
    //   → payments table SUM and bills.paid_amount got out of sync
    //
    // NEW (correct):
    //   bill.getPaidAmount() comes from DB (loaded by findById above)
    //   We never touch it directly in edit.
    //   If owner enters additionalPayment > 0 in the edit form,
    //   we call paymentService.recordPayment() which:
    //     1. Inserts into payments table
    //     2. Re-SUM all payments from DB
    //     3. Updates bills.paid_amount from that SUM
    //   → payments table and bills table always in sync. ✓
    // ─────────────────────────────────────────────────────────
    @PostMapping("/{id}/edit")
    public String saveEditedBill(@PathVariable Long id,
                                 @ModelAttribute("billForm") BillForm billForm,
                                 RedirectAttributes redirectAttributes) {
        try {
            // Load existing bill from DB — paidAmount comes from DB, not form
            Bill bill = billService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Bill not found: " + id));

            // Update due date
            if (billForm.getDueDate() != null && !billForm.getDueDate().isEmpty()) {
                try {
                    bill.setDueDate(LocalDate.parse(billForm.getDueDate().trim()));
                } catch (Exception ignored) {}
            } else {
                bill.setDueDate(null);
            }

            // Update notes
            bill.setNotes(billForm.getNotes());

            // DO NOT call bill.setPaidAmount() — paid amount is owned by payments table

            // Parse services
            List<BillServiceItem> serviceItems = new ArrayList<>();
            if (billForm.getServices() != null) {
                int order = 1;
                for (BillForm.ServiceLine line : billForm.getServices()) {
                    if (line.getServiceName() == null || line.getServiceName().trim().isEmpty()) continue;
                    if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

                    serviceItems.add(BillServiceItem.builder()
                            .serviceName(line.getServiceName().trim())
                            .description(line.getDescription() != null ? line.getDescription().trim() : null)
                            .amount(line.getAmount())
                            .sortOrder(order++)
                            .build());
                }
            }

            if (serviceItems.isEmpty()) throw new RuntimeException("Bill must have at least one service");

            // Update services + recalculate total/balance/status using DB paid amount
            billService.updateBillWithServices(bill, serviceItems);

            // Handle additional payment entered during edit
            // billForm.getInitialPayment() = the "Additional Payment Now" field in edit.html
            // This is NEW money received right now, not the total paid so far.
            BigDecimal additionalPayment = billForm.getInitialPayment() != null
                    ? billForm.getInitialPayment() : BigDecimal.ZERO;

            if (additionalPayment.compareTo(BigDecimal.ZERO) > 0) {
                // Reload bill to get fresh balance after service update
                Bill freshBill = billService.findById(id)
                        .orElseThrow(() -> new RuntimeException("Bill not found: " + id));

                // Cap additional payment at current balance (cannot overpay)
                if (additionalPayment.compareTo(freshBill.getBalanceAmount()) > 0) {
                    additionalPayment = freshBill.getBalanceAmount();
                }

                if (additionalPayment.compareTo(BigDecimal.ZERO) > 0) {
                    String payMode = billForm.getInitialPaymentMode();
                    if (payMode == null || payMode.trim().isEmpty()) payMode = "CASH";

                    Payment payment = Payment.builder()
                            .billId(id)
                            .amountPaid(additionalPayment)
                            .paymentMode(payMode)
                            .transactionReference(
                                    billForm.getInitialPaymentReference() != null
                                    && !billForm.getInitialPaymentReference().trim().isEmpty()
                                            ? billForm.getInitialPaymentReference().trim() : null)
                            .notes("Payment recorded during bill edit")
                            .build();

                    // This updates payments table AND recalculates bills.paid_amount from SUM
                    paymentService.recordPayment(payment);
                }
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "Bill " + bill.getBillNumber() + " updated successfully!");
            return "redirect:/bill/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating bill: " + e.getMessage());
            return "redirect:/bill/" + id + "/edit";
        }
    }

    // ─────────────────────────────────────────────────────────
    // AJAX: GET /bill/cars?customerId=5
    // ─────────────────────────────────────────────────────────
    @GetMapping("/cars")
    @ResponseBody
    public List<Car> getCarsForCustomer(@RequestParam Long customerId) {
        return customerService.getCarsForCustomer(customerId);
    }

    // ─────────────────────────────────────────────────────────
    // AJAX: GET /bill/api/customers/{customerId}/cars
    // ─────────────────────────────────────────────────────────
    @GetMapping("/api/customers/{customerId}/cars")
    @ResponseBody
    public List<Car> getCarsByCustomer(@PathVariable Long customerId) {
        return carRepository.findByCustomerId(customerId);
    }

    // ─────────────────────────────────────────────────────────
    // SAVE NEW BILL — POST /bill/save
    // ─────────────────────────────────────────────────────────
    @PostMapping("/save")
    public String saveBill(@ModelAttribute("billForm") BillForm billForm,
                           RedirectAttributes redirectAttributes) {
        try {
            // CUSTOMER
            Customer customer;
            if (billForm.getExistingCustomerId() != null && billForm.getExistingCustomerId() > 0) {
                customer = customerService.findById(billForm.getExistingCustomerId())
                        .orElseThrow(() -> new RuntimeException("Customer not found"));
            } else {
                if (billForm.getCustomerName() == null || billForm.getCustomerName().trim().isEmpty())
                    throw new RuntimeException("Customer name is required");
                if (billForm.getCustomerPhone() == null || billForm.getCustomerPhone().trim().isEmpty())
                    throw new RuntimeException("Customer phone is required");
                customer = customerService.findOrCreate(
                        billForm.getCustomerName().trim(),
                        billForm.getCustomerPhone().trim(),
                        billForm.getCustomerEmail());
            }

            // CAR
            Car car;
            if (billForm.getExistingCarId() != null && billForm.getExistingCarId() > 0) {
                car = carRepository.findById(billForm.getExistingCarId())
                        .orElseThrow(() -> new RuntimeException("Car not found"));
            } else {
                if (billForm.getCarNumber() == null || billForm.getCarNumber().trim().isEmpty())
                    throw new RuntimeException("Car number is required");
                String carNum = billForm.getCarNumber().trim().toUpperCase();
                if (carRepository.existsByCarNumber(carNum)) {
                    car = carRepository.findByCarNumber(carNum)
                            .orElseThrow(() -> new RuntimeException("Car lookup failed"));
                } else {
                    Car newCar = Car.builder()
                            .customerId(customer.getId())
                            .carNumber(carNum)
                            .carModel(billForm.getCarModel() != null ? billForm.getCarModel().trim() : "Unknown")
                            .brand(billForm.getCarBrand())
                            .manufactureYear(billForm.getCarManufactureYear())
                            .color(billForm.getCarColor())
                            .build();
                    Long carId = carRepository.save(newCar);
                    newCar.setId(carId);
                    car = newCar;
                }
            }

            // SERVICES
            List<BillServiceItem> serviceItems = new ArrayList<>();
            if (billForm.getServices() != null) {
                int order = 1;
                for (BillForm.ServiceLine line : billForm.getServices()) {
                    if (line.getServiceName() == null || line.getServiceName().trim().isEmpty()) continue;
                    if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) continue;
                    serviceItems.add(BillServiceItem.builder()
                            .serviceName(line.getServiceName().trim())
                            .description(line.getDescription() != null ? line.getDescription().trim() : null)
                            .amount(line.getAmount())
                            .sortOrder(order++)
                            .build());
                }
            }
            if (serviceItems.isEmpty()) throw new RuntimeException("Please add at least one service");

            // PAYMENT + DUE DATE
            BigDecimal initialPayment = billForm.getInitialPayment() != null
                    ? billForm.getInitialPayment() : BigDecimal.ZERO;

            LocalDate dueDate = null;
            if (billForm.getDueDate() != null && !billForm.getDueDate().trim().isEmpty()) {
                try { dueDate = LocalDate.parse(billForm.getDueDate().trim()); } catch (Exception ignored) {}
            }

            // BUILD BILL
            Bill bill = Bill.builder()
                    .customerId(customer.getId())
                    .carId(car.getId())
                    .paidAmount(initialPayment)
                    .dueDate(dueDate)
                    .notes(billForm.getNotes())
                    .discountAmount(billForm.getDiscountAmount() != null
                            ? billForm.getDiscountAmount() : BigDecimal.ZERO)
                    .build();

            Bill savedBill = billService.saveBillWithServices(bill, serviceItems);

            // RECORD INITIAL PAYMENT
            if (initialPayment.compareTo(BigDecimal.ZERO) > 0) {
                String payMode = billForm.getInitialPaymentMode();
                if (payMode == null || payMode.trim().isEmpty()) payMode = "CASH";

                paymentService.recordPayment(Payment.builder()
                        .billId(savedBill.getId())
                        .amountPaid(initialPayment)
                        .paymentMode(payMode)
                        .transactionReference(
                                billForm.getInitialPaymentReference() != null
                                && !billForm.getInitialPaymentReference().trim().isEmpty()
                                        ? billForm.getInitialPaymentReference().trim() : null)
                        .notes("Initial payment at bill creation")
                        .build());
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "Bill " + savedBill.getBillNumber() + " created successfully!");
            return "redirect:/bill/" + savedBill.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error creating bill: " + e.getMessage());
            return "redirect:/bill/new";
        }
    }

    // ─────────────────────────────────────────────────────────
    // VIEW BILL — GET /bill/{id}
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String viewBill(@PathVariable Long id, Model model) {
        Optional<Bill> billOpt = billService.findById(id);
        if (billOpt.isEmpty()) return "redirect:/dashboard";

        Bill bill = billOpt.get();
        model.addAttribute("bill", bill);
        model.addAttribute("gst", billService.getGstBreakdown(bill));
        model.addAttribute("payments", paymentService.getPaymentHistory(id));
        model.addAttribute("newPayment", new Payment());
        return "bill/detail";
    }

    // ─────────────────────────────────────────────────────────
    // PDF DOWNLOAD — GET /bill/{id}/pdf
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{id}/pdf")
    public void downloadPdf(@PathVariable Long id, HttpServletResponse response) {
        try {
            byte[] pdf = pdfService.generateBillPdf(id);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"Invoice-" + id + ".pdf\"");
            response.setContentLength(pdf.length);
            response.getOutputStream().write(pdf);
            response.getOutputStream().flush();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try { response.getWriter().write("Error: " + e.getMessage()); } catch (Exception ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────
    // BILL LIST — GET /bill/list
    // ─────────────────────────────────────────────────────────
    @GetMapping("/list")
    public String listBills(@RequestParam(value = "from", required = false) String from,
                            @RequestParam(value = "to",   required = false) String to,
                            Model model) {
        List<Bill> bills;
        boolean isFiltered = false;

        if (from != null && !from.isEmpty() && to != null && !to.isEmpty()) {
            try {
                bills = billService.getBillsByDateRange(LocalDate.parse(from), LocalDate.parse(to));
                isFiltered = true;
                model.addAttribute("fromDate", from);
                model.addAttribute("toDate",   to);
            } catch (Exception e) {
                bills = billService.getRecentBills(50);
            }
        } else {
            bills = billService.getRecentBills(50);
        }

        model.addAttribute("bills",      bills);
        model.addAttribute("billCount",  bills.size());
        model.addAttribute("isFiltered", isFiltered);
        return "bill/list";
    }
}

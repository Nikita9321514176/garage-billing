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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/bill")
public class BillController {

    @Autowired
    private BillService billService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private PaymentService paymentService;

    // ── PDF SERVICE ────────────────────────────────────────
    @Autowired
    private PdfService pdfService;

    // ─────────────────────────────────────────────────────────
    // SHOW BILL CREATION PAGE
    // URL: GET /bill/new
    // ─────────────────────────────────────────────────────────
    @GetMapping("/new")
    public String showNewBillForm(
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "carId", required = false) Long carId,
            Model model
    ) {

        BillForm billForm = new BillForm();

        // Pre-select customer/car if coming from another page
        if (customerId != null) {
            billForm.setExistingCustomerId(customerId);
        }

        if (carId != null) {
            billForm.setExistingCarId(carId);
        }

        model.addAttribute("billForm", billForm);

        // Customer dropdown data
        model.addAttribute(
                "customers",
                customerService.findAll()
        );

        // Car dropdown data
        if (customerId != null) {

            model.addAttribute(
                    "cars",
                    customerService.getCarsForCustomer(customerId)
            );

        } else {

            model.addAttribute(
                    "cars",
                    new ArrayList<>()
            );
        }

        return "bill/create";
    }

    // ─────────────────────────────────────────────────────────
    // SHOW EDIT FORM
    // URL: GET /bill/{id}/edit
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{id}/edit")
    public String showEditForm(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Load the bill with all its services
        Optional<Bill> billOpt = billService.findById(id);

        if (billOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Bill not found: " + id);
            return "redirect:/bill/list";
        }

        Bill bill = billOpt.get();

        // Pass the bill to the template
        model.addAttribute("bill", bill);
        model.addAttribute("services", bill.getServices());

        // Create BillForm for Thymeleaf form binding
        BillForm billForm = new BillForm();

        billForm.setInitialPayment(bill.getPaidAmount());
        billForm.setNotes(bill.getNotes());

        if (bill.getDueDate() != null) {
            billForm.setDueDate(bill.getDueDate().toString());
        }

        // Load service lines into form
        List<BillForm.ServiceLine> serviceLines =
                new ArrayList<>();

        if (bill.getServices() != null) {

            for (BillServiceItem item : bill.getServices()) {

                BillForm.ServiceLine line =
                        new BillForm.ServiceLine();

                line.setServiceName(item.getServiceName());
                line.setDescription(item.getDescription());
                line.setAmount(item.getAmount());

                serviceLines.add(line);
            }
        }

        billForm.setServices(serviceLines);

        model.addAttribute("billForm", billForm);

        // Load customers for dropdowns
        model.addAttribute(
                "customers",
                customerService.findAll()
        );

        return "bill/edit";
    }

    // ─────────────────────────────────────────────────────────
    // SAVE EDITED BILL
    // URL: POST /bill/{id}/edit
    // ─────────────────────────────────────────────────────────
    @PostMapping("/{id}/edit")
    public String saveEditedBill(
            @PathVariable Long id,
            @ModelAttribute("billForm") BillForm billForm,
            RedirectAttributes redirectAttributes) {

        try {

            // Load existing bill
            Bill bill = billService.findById(id)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Bill not found: " + id));

            // Update due date
            if (billForm.getDueDate() != null
                    && !billForm.getDueDate().isEmpty()) {

                try {

                    bill.setDueDate(
                            LocalDate.parse(
                                    billForm.getDueDate().trim()
                            )
                    );

                } catch (Exception ignored) {
                }
            }

            // Update notes
            bill.setNotes(billForm.getNotes());

            // Update paid amount
            if (billForm.getInitialPayment() != null) {
                bill.setPaidAmount(
                        billForm.getInitialPayment()
                );
            }

            // Parse edited services
            List<BillServiceItem> serviceItems =
                    new ArrayList<>();

            if (billForm.getServices() != null) {

                int order = 1;

                for (BillForm.ServiceLine line
                        : billForm.getServices()) {

                    // Skip blank rows
                    if (line.getServiceName() == null
                            || line.getServiceName()
                            .trim().isEmpty()) {
                        continue;
                    }

                    if (line.getAmount() == null
                            || line.getAmount()
                            .compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    serviceItems.add(

                            BillServiceItem.builder()
                                    .serviceName(
                                            line.getServiceName().trim()
                                    )
                                    .description(
                                            line.getDescription() != null
                                                    ? line.getDescription().trim()
                                                    : null
                                    )
                                    .amount(line.getAmount())
                                    .sortOrder(order++)
                                    .build()
                    );
                }
            }

            if (serviceItems.isEmpty()) {

                throw new RuntimeException(
                        "Bill must have at least one service"
                );
            }

            // Save updated bill
            billService.updateBillWithServices(
                    bill,
                    serviceItems
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Bill "
                            + bill.getBillNumber()
                            + " updated successfully!"
            );

            return "redirect:/bill/" + id;

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Error updating bill: " + e.getMessage()
            );

            return "redirect:/bill/" + id + "/edit";
        }
    }

    // ─────────────────────────────────────────────────────────
    // AJAX: LOAD CARS FOR CUSTOMER
    // URL: GET /bill/cars?customerId=5
    // ─────────────────────────────────────────────────────────
    @GetMapping("/cars")
    @ResponseBody
    public List<Car> getCarsForCustomer(
            @RequestParam Long customerId
    ) {

        return customerService.getCarsForCustomer(customerId);
    }

    // ─────────────────────────────────────────────────────────
    // AJAX API: LOAD CARS BY CUSTOMER ID
    // URL: /bill/api/customers/5/cars
    // ─────────────────────────────────────────────────────────
    @GetMapping("/api/customers/{customerId}/cars")
    @ResponseBody
    public List<Car> getCarsByCustomer(
            @PathVariable Long customerId
    ) {

        return carRepository.findByCustomerId(customerId);
    }

    // ─────────────────────────────────────────────────────────
    // SAVE BILL
    // URL: POST /bill/save
    // ─────────────────────────────────────────────────────────
    @PostMapping("/save")
    public String saveBill(
            @ModelAttribute("billForm") BillForm billForm,
            RedirectAttributes redirectAttributes
    ) {

        try {

            // ─────────────────────────────────────────
            // STEP 1: CUSTOMER
            // ─────────────────────────────────────────
            Customer customer;

            if (billForm.getExistingCustomerId() != null
                    && billForm.getExistingCustomerId() > 0) {

                customer = customerService
                        .findById(billForm.getExistingCustomerId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Customer not found: "
                                                + billForm.getExistingCustomerId()
                                )
                        );

            } else {

                if (billForm.getCustomerName() == null
                        || billForm.getCustomerName().trim().isEmpty()) {

                    throw new RuntimeException(
                            "Customer name is required"
                    );
                }

                if (billForm.getCustomerPhone() == null
                        || billForm.getCustomerPhone().trim().isEmpty()) {

                    throw new RuntimeException(
                            "Customer phone is required"
                    );
                }

                customer = customerService.findOrCreate(
                        billForm.getCustomerName().trim(),
                        billForm.getCustomerPhone().trim(),
                        billForm.getCustomerEmail()
                );
            }

            // ─────────────────────────────────────────
            // STEP 2: CAR
            // ─────────────────────────────────────────
            Car car;

            if (billForm.getExistingCarId() != null
                    && billForm.getExistingCarId() > 0) {

                car = carRepository
                        .findById(billForm.getExistingCarId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Car not found: "
                                                + billForm.getExistingCarId()
                                )
                        );

            } else {

                if (billForm.getCarNumber() == null
                        || billForm.getCarNumber().trim().isEmpty()) {

                    throw new RuntimeException(
                            "Car number is required"
                    );
                }

                String carNum =
                        billForm.getCarNumber()
                                .trim()
                                .toUpperCase();

                if (carRepository.existsByCarNumber(carNum)) {

                    car = carRepository
                            .findByCarNumber(carNum)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Car lookup failed: " + carNum
                                    )
                            );

                } else {

                    Car newCar = Car.builder()
                            .customerId(customer.getId())
                            .carNumber(carNum)
                            .carModel(
                                    billForm.getCarModel() != null
                                            ? billForm.getCarModel().trim()
                                            : "Unknown"
                            )
                            .brand(billForm.getCarBrand())
                            .build();

                    Long carId = carRepository.save(newCar);

                    newCar.setId(carId);

                    car = newCar;
                }
            }

            // ─────────────────────────────────────────
            // STEP 3: SERVICES
            // ─────────────────────────────────────────
            List<BillServiceItem> serviceItems =
                    new ArrayList<>();

            if (billForm.getServices() != null) {

                int order = 1;

                for (BillForm.ServiceLine line
                        : billForm.getServices()) {

                    if (line.getServiceName() == null
                            || line.getServiceName().trim().isEmpty()) {
                        continue;
                    }

                    if (line.getAmount() == null
                            || line.getAmount()
                            .compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    serviceItems.add(

                            BillServiceItem.builder()
                                    .serviceName(
                                            line.getServiceName().trim()
                                    )
                                    .description(
                                            line.getDescription() != null
                                                    ? line.getDescription().trim()
                                                    : null
                                    )
                                    .amount(line.getAmount())
                                    .sortOrder(order++)
                                    .build()
                    );
                }
            }

            if (serviceItems.isEmpty()) {

                throw new RuntimeException(
                        "Please add at least one service"
                );
            }

            // ─────────────────────────────────────────
            // STEP 4: PAYMENT + DUE DATE
            // ─────────────────────────────────────────
            BigDecimal initialPayment =
                    billForm.getInitialPayment() != null
                            ? billForm.getInitialPayment()
                            : BigDecimal.ZERO;

            LocalDate dueDate = null;

            if (billForm.getDueDate() != null
                    && !billForm.getDueDate().trim().isEmpty()) {

                try {

                    dueDate =
                            LocalDate.parse(
                                    billForm.getDueDate().trim()
                            );

                } catch (Exception ignored) {
                }
            }

         // ─────────────────────────────────────────
         // STEP 5: BUILD BILL
         // ─────────────────────────────────────────
         Bill bill = Bill.builder()
                 .customerId(customer.getId())
                 .carId(car.getId())
                 .paidAmount(initialPayment)
                 .dueDate(dueDate)
                 .notes(billForm.getNotes())
                 .build();

         // ─────────────────────────────────────────
         // SET DISCOUNT FROM FORM
         // ─────────────────────────────────────────
         BigDecimal discount =
                 billForm.getDiscountAmount() != null
                         ? billForm.getDiscountAmount()
                         : BigDecimal.ZERO;

         bill.setDiscountAmount(discount);

            // ─────────────────────────────────────────
            // STEP 6: SAVE BILL
            // ─────────────────────────────────────────
            Bill savedBill =
                    billService.saveBillWithServices(
                            bill,
                            serviceItems
                    );
         // ─────────────────────────────────────────
         // STEP 7: RECORD INITIAL PAYMENT
         // ONLY if amount > 0
         // ─────────────────────────────────────────
         if (initialPayment.compareTo(BigDecimal.ZERO) > 0) {

             String payMode = billForm.getInitialPaymentMode();

             // If amount entered but mode not selected
             if (payMode == null || payMode.trim().isEmpty()) {
                 payMode = "CASH";
             }

             Payment payment = Payment.builder()
                     .billId(savedBill.getId())
                     .amountPaid(initialPayment)
                     .paymentMode(payMode)

                     .transactionReference(
                             billForm.getInitialPaymentReference() != null
                             && !billForm.getInitialPaymentReference()
                                     .trim()
                                     .isEmpty()
                             ? billForm.getInitialPaymentReference()
                                     .trim()
                             : null
                     )

                     .notes("Initial payment at bill creation")
                     .build();

             paymentService.recordPayment(payment);
         }
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Bill "
                            + savedBill.getBillNumber()
                            + " created successfully!"
            );

            return "redirect:/bill/" + savedBill.getId();

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Error creating bill: " + e.getMessage()
            );

            return "redirect:/bill/new";
        }
    }

    // ─────────────────────────────────────────────────────────
    // VIEW BILL DETAIL
    // URL: GET /bill/5
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String viewBill(
            @PathVariable Long id,
            Model model
    ) {

        Optional<Bill> billOpt =
                billService.findById(id);

        if (billOpt.isEmpty()) {

            model.addAttribute(
                    "errorMessage",
                    "Bill not found: " + id
            );

            return "redirect:/dashboard";
        }

        Bill bill = billOpt.get();

        List<Payment> payments =
                paymentService.getPaymentHistory(id);

        model.addAttribute("bill", bill);
        model.addAttribute("payments", payments);

        model.addAttribute(
                "newPayment",
                new Payment()
        );

        return "bill/detail";
    }

    // ─────────────────────────────────────────────────────────
    // PDF DOWNLOAD ENDPOINT
    // URL: GET /bill/{id}/pdf
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{id}/pdf")
    public void downloadPdf(
            @PathVariable Long id,
            HttpServletResponse response) {

        try {

            byte[] pdfBytes =
                    pdfService.generateBillPdf(id);

            response.setContentType("application/pdf");

            response.setHeader(
                    "Content-Disposition",
                    "attachment; filename=\"Invoice-"
                            + id + ".pdf\"");

            response.setContentLength(pdfBytes.length);

            response.getOutputStream().write(pdfBytes);
            response.getOutputStream().flush();

        } catch (Exception e) {

            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            try {

                response.getWriter().write(
                        "Error generating PDF: "
                                + e.getMessage());

            } catch (Exception ignored) {
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // BILL LIST
    // URL: GET /bill/list
    // URL: GET /bill/list?from=2024-05-01&to=2024-05-31
    // ─────────────────────────────────────────────────────────
    @GetMapping("/list")
    public String listBills(
            @RequestParam(value = "from", required = false)
                    String from,

            @RequestParam(value = "to", required = false)
                    String to,

            Model model
    ) {

        List<Bill> bills;

        boolean isFiltered = false;

        // If BOTH dates are provided, filter by range
        if (from != null && !from.isEmpty()
                && to != null && !to.isEmpty()) {

            try {

                LocalDate fromDate =
                        LocalDate.parse(from);

                LocalDate toDate =
                        LocalDate.parse(to);

                bills = billService.getBillsByDateRange(
                        fromDate,
                        toDate
                );

                isFiltered = true;

                // Keep dates in form fields
                model.addAttribute("fromDate", from);
                model.addAttribute("toDate", to);

            } catch (Exception e) {

                // Invalid date format
                bills = billService.getRecentBills(50);
            }

        } else {

            // Default recent bills
            bills = billService.getRecentBills(50);
        }

        model.addAttribute("bills", bills);

        model.addAttribute(
                "billCount",
                bills.size()
        );

        model.addAttribute(
                "isFiltered",
                isFiltered
        );

        return "bill/list";
    }
}
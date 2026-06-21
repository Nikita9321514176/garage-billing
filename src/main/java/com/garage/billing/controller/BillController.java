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

    @Autowired private BillService     billService;
    @Autowired private CustomerService customerService;
    @Autowired private CarRepository   carRepository;
    @Autowired private PaymentService  paymentService;
    @Autowired private PdfService      pdfService;

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
    //
    // CHANGED: existing services are now split into two
    // pre-populated lists on the BillForm:
    //   - billForm.services → only LABOUR rows (existing behavior)
    //   - billForm.parts    → only PART rows (new)
    // The "services" model attribute (used by edit.html's
    // Thymeleaf display loop) is similarly split via two new
    // model attributes: "labourItems" and "partItems".
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
        model.addAttribute("bill", bill);

        // Split existing services by itemType for the edit page display loops
        List<BillServiceItem> labourItems = new ArrayList<>();
        List<BillServiceItem> partItems   = new ArrayList<>();
        if (bill.getServices() != null) {
            for (BillServiceItem item : bill.getServices()) {
                String type = item.getItemType() != null ? item.getItemType() : "LABOUR";
                if ("PART".equalsIgnoreCase(type)) {
                    partItems.add(item);
                } else {
                    labourItems.add(item);
                }
            }
        }
        model.addAttribute("labourItems", labourItems);
        model.addAttribute("partItems", partItems);

        BillForm billForm = new BillForm();
        billForm.setNotes(bill.getNotes());
        if (bill.getDueDate() != null) billForm.setDueDate(bill.getDueDate().toString());

        // Pre-populate LABOUR rows into billForm.services
        List<BillForm.ServiceLine> lines = new ArrayList<>();
        for (BillServiceItem item : labourItems) {
            BillForm.ServiceLine line = new BillForm.ServiceLine();
            line.setServiceName(item.getServiceName());
            line.setDescription(item.getDescription());
            line.setAmount(item.getAmount());
            lines.add(line);
        }
        billForm.setServices(lines);

        // NEW: Pre-populate PART rows into billForm.parts
        List<BillForm.PartLine> partLines = new ArrayList<>();
        for (BillServiceItem item : partItems) {
            BillForm.PartLine pl = new BillForm.PartLine();
            pl.setPartName(item.getServiceName());
            pl.setQty(item.getQty());
            pl.setUnitPrice(item.getUnitPrice());
            partLines.add(pl);
        }
        billForm.setParts(partLines);

        model.addAttribute("billForm", billForm);
        model.addAttribute("customers", customerService.findAll());

        return "bill/edit";
    }

    // ─────────────────────────────────────────────────────────
    // SAVE EDITED BILL — POST /bill/{id}/edit
    //
    // CHANGED: now builds BillServiceItem list from BOTH
    // billForm.services (LABOUR) and billForm.parts (PART),
    // then merges them before calling updateBillWithServices().
    // ─────────────────────────────────────────────────────────
    @PostMapping("/{id}/edit")
    public String saveEditedBill(@PathVariable Long id,
                                 @ModelAttribute("billForm") BillForm billForm,
                                 RedirectAttributes redirectAttributes) {
        try {
            Bill bill = billService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Bill not found: " + id));

            if (billForm.getDueDate() != null && !billForm.getDueDate().isEmpty()) {
                try {
                    bill.setDueDate(LocalDate.parse(billForm.getDueDate().trim()));
                } catch (Exception ignored) {}
            } else {
                bill.setDueDate(null);
            }

            bill.setNotes(billForm.getNotes());

            // Build the combined service items list (LABOUR + PART)
            List<BillServiceItem> serviceItems = buildServiceItems(billForm);

            if (serviceItems.isEmpty()) throw new RuntimeException("Bill must have at least one service or part");

            billService.updateBillWithServices(bill, serviceItems);

            BigDecimal additionalPayment = billForm.getInitialPayment() != null
                    ? billForm.getInitialPayment() : BigDecimal.ZERO;

            if (additionalPayment.compareTo(BigDecimal.ZERO) > 0) {
                Bill freshBill = billService.findById(id)
                        .orElseThrow(() -> new RuntimeException("Bill not found: " + id));

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
    //
    // CHANGED: serviceItems now built from buildServiceItems()
    // helper, which merges LABOUR (billForm.services) and
    // PART (billForm.parts) rows into one list.
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

            // SERVICES + PARTS (merged)
            List<BillServiceItem> serviceItems = buildServiceItems(billForm);
            if (serviceItems.isEmpty()) throw new RuntimeException("Please add at least one service or part");

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
    // NEW HELPER: buildServiceItems()
    //
    // Merges billForm.services (LABOUR) and billForm.parts (PART)
    // into one List<BillServiceItem>, ready to pass to
    // BillService.saveBillWithServices() / updateBillWithServices().
    //
    // LABOUR rows: amount = whatever the owner typed directly.
    // PART rows:   amount = qty * unitPrice (always computed here,
    //              never trusted from a hidden form field, so the
    //              math can never be tampered with or go stale).
    //
    // sortOrder is assigned sequentially across BOTH lists so the
    // services table (Labour) keeps its original relative order,
    // followed by parts — though PdfService re-splits them by
    // itemType anyway, so this ordering mainly matters for the
    // sort_order column's uniqueness, not visual order in the PDF.
    // ─────────────────────────────────────────────────────────
    private List<BillServiceItem> buildServiceItems(BillForm billForm) {

        List<BillServiceItem> items = new ArrayList<>();
        int order = 1;

        // ── LABOUR rows ────────────────────────────────────────
        if (billForm.getServices() != null) {
            for (BillForm.ServiceLine line : billForm.getServices()) {
                if (line.getServiceName() == null || line.getServiceName().trim().isEmpty()) continue;
                if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) continue;

                items.add(BillServiceItem.builder()
                        .serviceName(line.getServiceName().trim())
                        .description(line.getDescription() != null ? line.getDescription().trim() : null)
                        .amount(line.getAmount())
                        .itemType("LABOUR")
                        .qty(null)
                        .unitPrice(null)
                        .sortOrder(order++)
                        .build());
            }
        }

        // ── PART rows ──────────────────────────────────────────
        if (billForm.getParts() != null) {
            for (BillForm.PartLine part : billForm.getParts()) {
                if (part.getPartName() == null || part.getPartName().trim().isEmpty()) continue;

                BigDecimal qty = part.getQty() != null ? part.getQty() : BigDecimal.ZERO;
                BigDecimal unitPrice = part.getUnitPrice() != null ? part.getUnitPrice() : BigDecimal.ZERO;

                // Skip rows where qty or unit price wasn't filled in —
                // mirrors the existing "amount must be > 0" guard on services.
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

                // Amount is ALWAYS computed here — never trusted from
                // any client-submitted total, so it can't be tampered with.
                BigDecimal amount = qty.multiply(unitPrice);

                items.add(BillServiceItem.builder()
                        .serviceName(part.getPartName().trim())
                        .description(null)
                        .amount(amount)
                        .itemType("PART")
                        .qty(qty)
                        .unitPrice(unitPrice)
                        .sortOrder(order++)
                        .build());
            }
        }

        return items;
    }

    // ─────────────────────────────────────────────────────────
    // VIEW BILL — GET /bill/{id}
    //
    // CHANGED: now splits bill.getServices() into labourItems
    // and partItems, same pattern as showEditForm(), so
    // bill/detail.html can render two separate tables —
    // "Services Performed" (labour only) and "Parts Used"
    // (parts only) — instead of one merged list.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String viewBill(@PathVariable Long id, Model model) {
        Optional<Bill> billOpt = billService.findById(id);
        if (billOpt.isEmpty()) return "redirect:/dashboard";

        Bill bill = billOpt.get();

        List<BillServiceItem> labourItems = new ArrayList<>();
        List<BillServiceItem> partItems   = new ArrayList<>();
        if (bill.getServices() != null) {
            for (BillServiceItem item : bill.getServices()) {
                String type = item.getItemType() != null ? item.getItemType() : "LABOUR";
                if ("PART".equalsIgnoreCase(type)) {
                    partItems.add(item);
                } else {
                    labourItems.add(item);
                }
            }
        }

        model.addAttribute("bill", bill);
        model.addAttribute("labourItems", labourItems);
        model.addAttribute("partItems", partItems);
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
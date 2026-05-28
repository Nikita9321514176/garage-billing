package com.garage.billing.controller;

import com.garage.billing.model.Bill;
import com.garage.billing.model.Car;
import com.garage.billing.model.Customer;
import com.garage.billing.repository.CarRepository;
import com.garage.billing.service.BillService;
import com.garage.billing.service.CustomerService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/car")
public class CarController {

    @Autowired private CarRepository   carRepository;
    @Autowired private CustomerService customerService;
    @Autowired private BillService     billService;

    // ── SHOW NEW CAR FORM ──────────────────────────────────
    // GET /car/new
    // GET /car/new?customerId=5  (pre-select a customer)
    @GetMapping("/new")
    public String showNewCarForm(
            @RequestParam(value = "customerId", required = false)
                Long customerId,
            Model model
    ) {
        model.addAttribute("car", new Car());
        model.addAttribute("customers", customerService.findAll());

        if (customerId != null) {
            model.addAttribute("preselectedCustomerId", customerId);
        }
        return "car/form";
    }

    // ── SAVE NEW CAR ───────────────────────────────────────
    // POST /car/save
    @PostMapping("/save")
    public String saveCar(
            @RequestParam("customerId")   Long   customerId,
            @RequestParam("carNumber")    String carNumber,
            @RequestParam("carModel")     String carModel,
            @RequestParam(value = "brand",           required = false)
                String  brand,
            @RequestParam(value = "manufactureYear", required = false)
                Integer manufactureYear,
            @RequestParam(value = "color",           required = false)
                String  color,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String upperCar = carNumber.trim().toUpperCase();

            if (carRepository.existsByCarNumber(upperCar)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Car number " + upperCar + " is already registered!");
                return "redirect:/car/new?customerId=" + customerId;
            }

            Car car = Car.builder()
                .customerId(customerId)
                .carNumber(upperCar)
                .carModel(carModel.trim())
                .brand(brand)
                .manufactureYear(manufactureYear)
                .color(color)
                .build();

            carRepository.save(car);

            redirectAttributes.addFlashAttribute("successMessage",
                "Car " + upperCar + " registered successfully!");

            return "redirect:/customer/" + customerId;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Error saving car: " + e.getMessage());
            return "redirect:/car/new";
        }
    }

    // ── SEARCH CAR BY NUMBER — MAIN FEATURE ───────────────
    // GET /car/search
    // GET /car/search?carNumber=MH12AB1234
    @GetMapping("/search")
    public String searchCar(
            @RequestParam(value = "carNumber", required = false)
                String carNumber,
            Model model
    ) {
        // No search term — show blank search page
        if (carNumber == null || carNumber.trim().isEmpty()) {
            return "car/search";
        }

        String upper = carNumber.trim().toUpperCase();
        model.addAttribute("searchCarNumber", upper);

        // Step 1: Find the car
        Optional<Car> carOpt = carRepository.findByCarNumber(upper);

        if (carOpt.isEmpty()) {
            // Car not found
            model.addAttribute("notFound", true);
            return "car/search";
        }

        Car car = carOpt.get();
        model.addAttribute("car", car);

        // Step 2: Get customer details
        Optional<Customer> custOpt =
            customerService.findById(car.getCustomerId());
        custOpt.ifPresent(c -> model.addAttribute("customer", c));

        // Step 3: Get full bill history with all services loaded
        List<Bill> billHistory = billService.getCarHistory(upper);
        model.addAttribute("billHistory", billHistory);
        model.addAttribute("billCount", billHistory.size());

        // Step 4: Lifetime summary stats (single DB query)
        Map<String, Object> summary =
            billService.getCarLifetimeSummary(upper);
        model.addAttribute("summary", summary);

        // Step 5: Calculate totals from bill list
        // (cross-check with summary, also needed for display)
        BigDecimal totalEarned = billHistory.stream()
            .map(Bill::getTotalAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPending = billHistory.stream()
            .map(Bill::getBalanceAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = billHistory.stream()
            .map(Bill::getPaidAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("totalEarned",  totalEarned);
        model.addAttribute("totalPending", totalPending);
        model.addAttribute("totalPaid",    totalPaid);

        // Step 6: Last visit date from most recent bill
        // billHistory is ordered newest first so get(0) = latest
        if (!billHistory.isEmpty() && billHistory.get(0).getBillDate() != null) {
            model.addAttribute("lastVisit",
                billHistory.get(0).getBillDate());
        }

        return "car/search";
    }
}
package com.garage.billing.controller;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;

import com.garage.billing.model.Customer;
import com.garage.billing.model.Car;
import com.garage.billing.service.CustomerService;
import com.garage.billing.repository.CarRepository;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/customer")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CarRepository carRepository;

    // ── LIST ALL CUSTOMERS ─────────────────────────────────
    @GetMapping("/list")
    public String listCustomers(
            // Optional search query — if present, filter by name or phone
            // If absent (null), show all customers as before
            @RequestParam(value = "search", required = false)
                String search,
            Model model
    ) {
        List<Customer> customers;

        if (search != null && !search.trim().isEmpty()) {
            // Search mode — filter by name or phone
            customers = customerService.searchByNameOrPhone(
                search.trim());
            model.addAttribute("searchQuery", search.trim());
        } else {
            // Normal mode — show all customers
            customers = customerService.findAll();
            model.addAttribute("searchQuery", "");
        }

        model.addAttribute("customers", customers);
        model.addAttribute("totalCount", customers.size());
        return "customer/list";
    }

    // ── SHOW NEW CUSTOMER FORM ─────────────────────────────
    @GetMapping("/new")
    public String showNewCustomerForm(Model model) {

        model.addAttribute("customer", new Customer());

        return "customer/form";
    }

    // ── SAVE CUSTOMER ──────────────────────────────────────
    @PostMapping("/save")
    public String saveCustomer(
            @RequestParam("name") String name,
            @RequestParam("phone") String phone,
            @RequestParam(value = "email", required = false) String email,
            RedirectAttributes redirectAttributes
    ) {

        try {

            Customer customer =
                    customerService.findOrCreate(name, phone, email);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Customer '" + customer.getName() + "' saved successfully!"
            );

            return "redirect:/customer/" + customer.getId();

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Error saving customer: " + e.getMessage()
            );

            return "redirect:/customer/new";
        }
    }

    // ── VIEW CUSTOMER PROFILE ──────────────────────────────
    @GetMapping("/{id}")
    public String viewCustomer(
            @PathVariable Long id,
            Model model
    ) {

        Optional<Customer> customerOpt =
                customerService.findById(id);

        if (customerOpt.isEmpty()) {

            model.addAttribute(
                    "errorMessage",
                    "Customer not found with ID: " + id
            );

            return "customer/list";
        }

        Customer customer = customerOpt.get();

        List<Car> cars =
                customerService.getCarsForCustomer(id);

        model.addAttribute("customer", customer);
        model.addAttribute("cars", cars);
        model.addAttribute("carCount", cars.size());

        return "customer/detail";
    }

    // ── SEARCH CUSTOMER BY PHONE ───────────────────────────
    @GetMapping("/search")
    public String searchCustomer(
            @RequestParam(value = "phone", required = false)
            String phone,
            Model model
    ) {

        if (phone == null || phone.trim().isEmpty()) {
            return "customer/search";
        }

        Optional<Customer> result =
                customerService.findByPhone(phone.trim());

        if (result.isPresent()) {

            Customer customer = result.get();

            List<Car> cars =
                    customerService.getCarsForCustomer(
                            customer.getId()
                    );

            model.addAttribute("customer", customer);
            model.addAttribute("cars", cars);
            model.addAttribute("searchPhone", phone);

        } else {

            model.addAttribute("notFound", true);
            model.addAttribute("searchPhone", phone);
        }

        return "customer/search";
    }

    // ── SEARCH CUSTOMER BY NAME ────────────────────────────
    @GetMapping("/searchByName")
    public String searchByName(
            @RequestParam(value = "name", required = false)
            String name,
            Model model
    ) {

        if (name == null || name.trim().isEmpty()) {
            return "customer/search";
        }

        List<Customer> results =
                customerService.searchByName(name);

        model.addAttribute("searchResults", results);
        model.addAttribute("searchName", name);
        model.addAttribute("resultCount", results.size());

        return "customer/search";
    }

    // ── AJAX CUSTOMER SEARCH FOR AUTOCOMPLETE ────────────────
    @GetMapping("/api/search")
    @ResponseBody
    public List<Customer> searchCustomersApi(
            @RequestParam("q") String query
    ) {

        if (query == null || query.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return customerService.searchByNameOrPhone(
                query.trim()
        );
    }
}
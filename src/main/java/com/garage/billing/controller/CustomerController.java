package com.garage.billing.controller;

// Model = Spring's object for passing data from Controller to HTML template.
// Think of it as a Map<String, Object> that Thymeleaf can read.
import org.springframework.ui.Model;

// @Controller = marks this class as an MVC controller.
// It handles HTTP requests and returns the name of an HTML template to render.
// (NOT @RestController — that returns JSON/text directly, not HTML pages)
import org.springframework.stereotype.Controller;

// URL mapping annotations
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

// RedirectAttributes = carries flash messages across a redirect
// After a POST save, we redirect to GET (PRG pattern).
// Flash attributes survive exactly one redirect then disappear.
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Validation
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;

import com.garage.billing.model.Customer;
import com.garage.billing.model.Car;
import com.garage.billing.service.CustomerService;
import com.garage.billing.repository.CarRepository;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

// @RequestMapping("/customer") = ALL methods in this controller start with /customer
// So @GetMapping("/list") becomes GET /customer/list
// And @PostMapping("/save") becomes POST /customer/save
@Controller
@RequestMapping("/customer")
public class CustomerController {

    // Spring injects these automatically because they are @Service and @Repository beans
    @Autowired
    private CustomerService customerService;

    @Autowired
    private CarRepository carRepository;

    // ── LIST ALL CUSTOMERS ─────────────────────────────────
    // URL: GET http://localhost:8080/customer/list
    //
    // Model model = Spring passes this in automatically.
    // We put data INTO it, Thymeleaf reads OUT of it.
    @GetMapping("/list")
    public String listCustomers(Model model) {

        // Get all customers from DB via service → repository
        List<Customer> customers = customerService.findAll();

        // model.addAttribute("customers", customers)
        // This makes the 'customers' variable available in HTML as:
        // th:each="customer : ${customers}"
        model.addAttribute("customers", customers);

        // Total count for display
        model.addAttribute("totalCount", customers.size());

        // Return value = template name (without .html extension)
        // Thymeleaf looks for: src/main/resources/templates/customer/list.html
        return "customer/list";
    }

    // ── SHOW NEW CUSTOMER FORM ─────────────────────────────
    // URL: GET http://localhost:8080/customer/new
    @GetMapping("/new")
    public String showNewCustomerForm(Model model) {

        // We add an empty Customer object to the model.
        // Thymeleaf binds this to the form fields using th:object="${customer}"
        // When the user fills the form and submits, Spring maps
        // the form fields back into a Customer object in the POST method.
        model.addAttribute("customer", new Customer());

        return "customer/form";
    }

    // ── SAVE NEW CUSTOMER ──────────────────────────────────
    // URL: POST http://localhost:8080/customer/save
    //
    // @RequestParam = reads individual form field values
    // We read them manually here (instead of binding to Customer object)
    // because we also need to handle the car details simultaneously.
    @PostMapping("/save")
    public String saveCustomer(
            // @RequestParam("name") = reads the form field named "name"
            // required = true means Spring throws error if field is missing
            @RequestParam("name")  String name,
            @RequestParam("phone") String phone,
            // required = false = optional field
            @RequestParam(value = "email", required = false) String email,
            RedirectAttributes redirectAttributes  // for flash messages
    ) {
        try {
            // Business logic in service layer — not in controller
            // findOrCreate: if customer with this phone exists, reuse them
            //               if not, create a new customer record
            Customer customer = customerService.findOrCreate(name, phone, email);

            // RedirectAttributes.addFlashAttribute() stores a message
            // that survives exactly ONE redirect and then disappears.
            // This implements the PRG (Post-Redirect-Get) pattern:
            // POST /customer/save → redirect → GET /customer/{id}
            // Without PRG, refreshing the page re-submits the form!
            redirectAttributes.addFlashAttribute("successMessage",
                "Customer '" + customer.getName() + "' saved successfully!");

            // "redirect:/customer/" + id means Spring sends:
            //   HTTP 302 Found → Location: /customer/5
            // Browser automatically follows this redirect with a GET request.
            return "redirect:/customer/" + customer.getId();

        } catch (Exception e) {
            // Something went wrong — go back to form with error
            redirectAttributes.addFlashAttribute("errorMessage",
                "Error saving customer: " + e.getMessage());
            return "redirect:/customer/new";
        }
    }

    // ── VIEW CUSTOMER PROFILE ──────────────────────────────
    // URL: GET http://localhost:8080/customer/5  (for customer with id=5)
    //
    // @PathVariable = reads a value from the URL path itself
    // {id} in the mapping becomes the 'id' parameter here
    @GetMapping("/{id}")
    public String viewCustomer(
            @PathVariable Long id,
            Model model
    ) {
        // Optional = might be present or might be empty
        // This is safer than returning null
        Optional<Customer> customerOpt = customerService.findById(id);

        if (customerOpt.isEmpty()) {
            // Customer not found — send to list page with error
            // We can't use redirectAttributes here (no RedirectAttributes param)
            // so we add the error to the model for the current request
            model.addAttribute("errorMessage", "Customer not found with ID: " + id);
            return "customer/list";
        }

        Customer customer = customerOpt.get();

        // Get all cars belonging to this customer
        List<Car> cars = customerService.getCarsForCustomer(id);

        model.addAttribute("customer", customer);
        model.addAttribute("cars", cars);
        model.addAttribute("carCount", cars.size());

        return "customer/detail";
    }

    // ── SEARCH CUSTOMER BY PHONE ───────────────────────────
    // URL: GET http://localhost:8080/customer/search?phone=9876543210
    //
    // @RequestParam(value = "phone", required = false)
    // required = false means this URL also works without ?phone=
    // In that case, phone will be null.
    @GetMapping("/search")
    public String searchCustomer(
            @RequestParam(value = "phone", required = false) String phone,
            Model model
    ) {
        // If no phone given, just show empty search form
        if (phone == null || phone.trim().isEmpty()) {
            return "customer/search";
        }

        Optional<Customer> result = customerService.findByPhone(phone.trim());

        if (result.isPresent()) {
            Customer customer = result.get();
            List<Car> cars = customerService.getCarsForCustomer(customer.getId());
            model.addAttribute("customer", customer);
            model.addAttribute("cars", cars);
            model.addAttribute("searchPhone", phone);
        } else {
            // No customer found with this phone
            model.addAttribute("notFound", true);
            model.addAttribute("searchPhone", phone);
        }

        return "customer/search";
    }
}
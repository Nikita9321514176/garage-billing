package com.garage.billing.service;

import com.garage.billing.model.Customer;
import com.garage.billing.model.Car;
import com.garage.billing.repository.CustomerRepository;
import com.garage.billing.repository.CarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// @Service = marks this as a Spring service bean
// Functionally identical to @Component, but communicates intent:
// "this class contains business logic"
@Service
public class CustomerService {

    // Spring injects both repositories automatically
    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CarRepository carRepository;

    // Business rule: if customer exists (same phone), return existing one
    // If not, create and return new customer
    // This is called "find or create" pattern
    public Customer findOrCreate(String name, String phone, String email) {
        Optional<Customer> existing = customerRepository.findByPhone(phone);
        if (existing.isPresent()) {
            return existing.get(); // customer already in DB, reuse
        }
        // New customer — build and save
        Customer newCustomer = Customer.builder()
            .name(name)
            .phone(phone)
            .email(email)
            .build();
        Long id = customerRepository.save(newCustomer);
        newCustomer.setId(id);
        return newCustomer;
    }

    public Optional<Customer> findById(Long id) {
        return customerRepository.findById(id);
    }

    public Optional<Customer> findByPhone(String phone) {
        return customerRepository.findByPhone(phone);
    }

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    // Get all cars belonging to a customer
    public List<Car> getCarsForCustomer(Long customerId) {
        return carRepository.findByCustomerId(customerId);
    }
}
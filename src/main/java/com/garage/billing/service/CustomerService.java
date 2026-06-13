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
@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CarRepository carRepository;

    // Find existing customer by phone or create new one
    public Customer findOrCreate(String name, String phone, String email) {

        Optional<Customer> existing =
                customerRepository.findByPhone(phone);

        if (existing.isPresent()) {
            return existing.get();
        }

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

    // SEARCH CUSTOMERS BY NAME
    public List<Customer> searchByName(String name) {

        if (name == null || name.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return customerRepository.searchByName(name);
    }

    // SEARCH CUSTOMERS BY NAME OR PHONE
    public List<Customer> searchByNameOrPhone(String query) {

        if (query == null || query.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return customerRepository.searchByNameOrPhone(query);
    }

    // Get all cars belonging to a customer
    public List<Car> getCarsForCustomer(Long customerId) {
        return carRepository.findByCustomerId(customerId);
    }
}
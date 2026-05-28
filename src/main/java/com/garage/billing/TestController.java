package com.garage.billing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/test")
    public String testConnection() {

        return "Garage Billing System is running! Spring Boot + Eclipse setup is working perfectly.";

    }
}
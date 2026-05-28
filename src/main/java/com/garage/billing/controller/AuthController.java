package com.garage.billing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    // ── SHOW LOGIN PAGE ──────────────────────────────────────
    // URL: GET /login
    //
    // Spring Security redirects here automatically when
    // an unauthenticated user tries to access any page.
    //
    // We do NOT handle POST /login — Spring Security handles
    // that itself. It reads the "username" and "password"
    // form fields, calls UserDetailsService, and either:
    //   - success → redirect to /dashboard
    //   - failure → redirect to /login?error=true
    @GetMapping("/login")
    public String loginPage(
            // ?error=true is added by Spring Security when login fails
            @RequestParam(value = "error",
                required = false) String error,
            // ?logout=true is added after a successful logout
            @RequestParam(value = "logout",
                required = false) String logout,
            Model model
    ) {
        // If error parameter is present in the URL,
        // tell the template to show the error message
        if (error != null) {
            model.addAttribute("loginError", true);
            model.addAttribute("errorMessage",
                "Incorrect username or password. Please try again.");
        }

        // If logout parameter is present,
        // tell the template to show the logout success message
        if (logout != null) {
            model.addAttribute("logoutSuccess", true);
        }

        // Return the login template
        // Thymeleaf looks for: templates/auth/login.html
        return "auth/login";
    }
}
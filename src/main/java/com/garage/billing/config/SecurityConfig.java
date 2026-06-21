package com.garage.billing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

// @Configuration tells Spring: "read this class for bean definitions"
// @EnableWebSecurity activates Spring Security for the whole application
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Read values from application.properties
    // The :admin and :Garage@2024 are default values — used if
    // the property is missing. Always define it in properties though.
    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:Garage@2024}")
    private String adminPassword;

    // ── BEAN 1: SecurityFilterChain ─────────────────────────
    // This bean defines ALL the security rules.
    // Spring Security reads this and applies every rule
    // to every incoming HTTP request.
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            // ── RULE 1: WHO CAN ACCESS WHAT ─────────────────
            .authorizeHttpRequests(auth -> auth

                // These URLs are PUBLIC — no login needed.
                // Why? Because if CSS and JS required login,
                // the login page itself would have no styling!
                // **/*.css matches any CSS file in any folder.
                // /webjars/** = Bootstrap and other web libraries.
            		.requestMatchers(
            			    "/css/**",
            			    "/js/**",
            			    "/images/**",
            			    "/favicon.ico",
            			    "/invoice/**"
            			).permitAll()
                // The login page itself must be public.
                // If /login required login, nobody could ever log in.
                .requestMatchers("/login").permitAll()

                // EVERYTHING ELSE requires the user to be logged in.
                // This single line protects ALL your pages:
                // /dashboard, /bill/**, /customer/**, /car/**, etc.
                // You never need to add security individually per page.
                .anyRequest().authenticated()
            )

            // ── RULE 2: CUSTOM LOGIN FORM ────────────────────
            .formLogin(form -> form

                // URL of our custom login HTML page (GET request)
                // When Spring Security needs to show the login form,
                // it redirects to this URL.
                .loginPage("/login")

                // URL that receives the submitted login form (POST).
                // Spring Security handles this automatically —
                // you do NOT need to write a controller method for it.
                // Spring reads the "username" and "password" fields
                // from the form and verifies them.
                .loginProcessingUrl("/login")

                // After successful login, go to dashboard.
                // true = always go to dashboard even if the user
                // tried to access a different page first.
                // Set to false if you want to redirect them back
                // to the page they originally tried to visit.
                .defaultSuccessUrl("/dashboard", true)

                // After failed login (wrong password), go back to
                // login page with ?error=true in the URL.
                // Our login page reads this and shows an error message.
                .failureUrl("/login?error=true")

                // The login page itself is public (permit all)
                .permitAll()
            )

            // ── RULE 3: LOGOUT ───────────────────────────────
            .logout(logout -> logout

                // URL that triggers logout. Must be POST (not GET).
                // Why POST? A GET logout could be triggered by a
                // malicious image tag: <img src="/logout"> on any site.
                // POST requires a form with a CSRF token — safe.
                .logoutUrl("/logout")

                // After logout, go to login page.
                // ?logout=true tells the login page to show
                // a "you have been logged out" message.
                .logoutSuccessUrl("/login?logout=true")

                // Destroy the server-side session data
                .invalidateHttpSession(true)

                // Delete the session cookie from the browser
                .deleteCookies("JSESSIONID")

                .permitAll()
            );

        // Build and return the configured security rules
        return http.build();
    }

    // ── BEAN 2: UserDetailsService ───────────────────────────
    // This bean answers the question: "who is allowed to log in?"
    // InMemoryUserDetailsManager stores users in RAM.
    // Perfect for a single-owner garage app.
    // For multiple employees, you would switch to a database.
    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder encoder) {

        // Build the admin user object
        UserDetails admin = User.builder()
            // Username — what you type in the form
            .username(adminUsername)
            // Password — BCrypt hashed before storing
            // encoder.encode() runs BCrypt on the plain password
            // so "Garage@2024" becomes "$2a$10$abc123..."
            .password(encoder.encode(adminPassword))
            // Role — "ADMIN" (Spring internally stores as "ROLE_ADMIN")
            // Used for authorization — not needed now but good practice
            .roles("ADMIN")
            .build();

        // Store this user in memory
        // At login time, Spring calls this service,
        // finds the user by username, and compares passwords
        return new InMemoryUserDetailsManager(admin);
    }

    // ── BEAN 3: PasswordEncoder ──────────────────────────────
    // BCryptPasswordEncoder is the industry standard.
    // It is intentionally slow (takes ~100ms per hash) to
    // make brute-force attacks impractical.
    // It automatically adds a random "salt" so even two
    // identical passwords produce different hashes.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
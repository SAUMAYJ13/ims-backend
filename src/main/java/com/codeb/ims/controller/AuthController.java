package com.codeb.ims.controller;

import com.codeb.ims.entity.User;
import com.codeb.ims.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // ✅ ADDED

    @Value("${codeb.brevo.apikey}")
    private String brevoApiKey;

    @Value("${codeb.brevo.sender}")
    private String senderEmail;

    // --- 1. REGISTER ---
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            if (userRepository.findByEmail(user.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body("Error: Email already exists");
            }

            // ✅ ENCODE PASSWORD
            user.setPassword(passwordEncoder.encode(user.getPassword()));

            // Default role
            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("STAFF");
            }

            return ResponseEntity.ok(userRepository.save(user));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // --- 2. LOGIN ---
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        try {
            String email = loginData.get("email");
            String password = loginData.get("password");

            Optional<User> userOptional = userRepository.findByEmail(email);

            if (userOptional.isPresent()) {
                User user = userOptional.get();

                // ✅ FIXED PASSWORD CHECK
                if (passwordEncoder.matches(password, user.getPassword())) {
                    return ResponseEntity.ok(user);
                }
            }

            return ResponseEntity.status(401).body("Invalid Credentials");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // --- 3. FORGOT PASSWORD ---
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            try {
                String resetLink = "https://ims-frontend-psi.vercel.app/authentication/reset-password?email=" + email;

                String htmlContent = "<html><body>" +
                        "<h3>Code-B IMS Password Reset</h3>" +
                        "<p>Hello " + userOptional.get().getFullName() + ",</p>" +
                        "<p>Click the link below to reset your password:</p>" +
                        "<a href='" + resetLink + "'>Reset Password Here</a>" +
                        "<p>If you did not request this, ignore this email.</p>" +
                        "</body></html>";

                Map<String, Object> body = new HashMap<>();

                Map<String, String> sender = new HashMap<>();
                sender.put("name", "Code-B IMS");
                sender.put("email", senderEmail);
                body.put("sender", sender);

                List<Map<String, String>> toList = new ArrayList<>();
                Map<String, String> to = new HashMap<>();
                to.put("email", email);
                toList.add(to);
                body.put("to", toList);

                body.put("subject", "Reset Your Password");
                body.put("htmlContent", htmlContent);

                String apiUrl = "https://api.brevo.com/v3/smtp/email";

                HttpHeaders headers = new HttpHeaders();
                headers.set("api-key", brevoApiKey);
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                RestTemplate restTemplate = new RestTemplate();

                ResponseEntity<String> response =
                        restTemplate.postForEntity(apiUrl, entity, String.class);

                if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                    return ResponseEntity.ok("✅ Reset link sent successfully!");
                } else {
                    return ResponseEntity.status(500).body("❌ Email failed: " + response.getBody());
                }

            } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(500).body("❌ Error: " + e.getMessage());
            }
        }

        return ResponseEntity.status(404).body("❌ Email not found.");
    }

    // --- 4. RESET PASSWORD ---
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String newPassword = request.get("password");

            Optional<User> userOptional = userRepository.findByEmail(email);

            if (userOptional.isPresent()) {
                User user = userOptional.get();

                // ✅ ENCODE NEW PASSWORD
                user.setPassword(passwordEncoder.encode(newPassword));

                userRepository.save(user);
                return ResponseEntity.ok("✅ Password updated!");
            }

            return ResponseEntity.status(404).body("❌ User not found.");

        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Error: " + e.getMessage());
        }
    }

    // --- 5. UPDATE PROFILE ---
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id,
                                        @RequestBody Map<String, String> updates) {

        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            if (updates.containsKey("fullName"))
                user.setFullName(updates.get("fullName"));

            if (updates.containsKey("email"))
                user.setEmail(updates.get("email"));

            if (updates.containsKey("password") && !updates.get("password").isEmpty()) {
                // ✅ ENCODE UPDATED PASSWORD
                user.setPassword(passwordEncoder.encode(updates.get("password")));
            }

            return ResponseEntity.ok(userRepository.save(user));
        }

        return ResponseEntity.notFound().build();
    }

    // --- 6. CREATE ADMIN ---
    @PostMapping("/create-admin")
    public ResponseEntity<?> createAdmin(@RequestBody User user) {
        try {
            if (userRepository.findByEmail(user.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body("Error: Email already exists");
            }

            user.setRole("ADMIN");
            user.setStatus("active");

            // ✅ ENCODE PASSWORD
            user.setPassword(passwordEncoder.encode(user.getPassword()));

            return ResponseEntity.ok(userRepository.save(user));

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
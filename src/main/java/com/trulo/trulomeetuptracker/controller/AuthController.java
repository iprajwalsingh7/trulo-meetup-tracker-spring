package com.trulo.trulomeetuptracker.controller;

import com.trulo.trulomeetuptracker.dto.AuthResponse;
import com.trulo.trulomeetuptracker.dto.LoginRequest;
import com.trulo.trulomeetuptracker.dto.RegisterRequest;
import com.trulo.trulomeetuptracker.model.User;
import com.trulo.trulomeetuptracker.repository.UserRepository;
import com.trulo.trulomeetuptracker.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())
                || userRepository.existsByUsername(request.getUsername())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "User with this email or username already exists");
            return ResponseEntity.badRequest().body(response);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .isOnline(false)
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponse.builder()
                        .message("User created successfully")
                        .token(token)
                        .user(stripPassword(user))
                        .build());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Invalid credentials");
            return ResponseEntity.badRequest().body(response);
        }

        user.setOnline(true);
        user.setLastSeen(new Date());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId());

        return ResponseEntity.ok(AuthResponse.builder()
                .message("Login successful")
                .token(token)
                .user(stripPassword(user))
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Map<String, Object> response = new HashMap<>();
        response.put("user", stripPassword(user));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.setOnline(false);
        user.setLastSeen(new Date());
        userRepository.save(user);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Logout successful");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/search")
    public ResponseEntity<?> searchUsers(@RequestParam String query) {
        if (query == null || query.length() < 2) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Query must be at least 2 characters");
            return ResponseEntity.badRequest().body(response);
        }

        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // MongoDB Regex Search on multiple fields, excluding current user
        List<User> users = userRepository.findAll().stream() // Simplified for now
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(query.toLowerCase())) ||
                        (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(query.toLowerCase()))
                        ||
                        (u.getEmail() != null && u.getEmail().toLowerCase().contains(query.toLowerCase())))
                .limit(10)
                .map(this::stripPassword)
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("users", users);
        return ResponseEntity.ok(response);
    }

    private User stripPassword(User user) {
        User safeUser = User.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatar(user.getAvatar())
                .isOnline(user.isOnline())
                .lastSeen(user.getLastSeen())
                .friends(user.getFriends())
                .meetups(user.getMeetups())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
        return safeUser;
    }
}

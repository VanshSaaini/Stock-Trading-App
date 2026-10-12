package com.trading.userservice.controller;

import com.trading.userservice.dto.AuthResponse;
import com.trading.userservice.dto.LoginRequest;
import com.trading.userservice.dto.RegisterRequest;
import com.trading.userservice.dto.UserResponse;
import com.trading.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Public API - reachable through the api-gateway.
 * X-User-Id is set by the gateway from the verified JWT.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(
            @RequestHeader("X-User-Id") String userId) {

        return ResponseEntity.ok(userService.getUserById(userId));
    }

    /** Add money to MY wallet (a user can only top up their own account) */
    @PostMapping("/me/funds/add")
    public ResponseEntity<UserResponse> addFunds(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("amount") BigDecimal amount) {

        return ResponseEntity.ok(userService.addFunds(userId, amount));
    }

}

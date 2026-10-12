package com.trading.userservice.controller;

import com.trading.userservice.dto.UserResponse;
import com.trading.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Service-to-service API (called by order-service).
 *
 * IMPORTANT: the api-gateway has NO route for /internal/**, so these endpoints
 * are not reachable from outside. Keep it that way - they move money for any userId.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @PostMapping("/{userId}/funds/deduct")
    public ResponseEntity<UserResponse> deductFunds(
            @PathVariable("userId") String userId,
            @RequestParam("amount") BigDecimal amount) {

        return ResponseEntity.ok(userService.deductFunds(userId, amount));
    }

    @PostMapping("/{userId}/funds/credit")
    public ResponseEntity<UserResponse> creditFunds(
            @PathVariable("userId") String userId,
            @RequestParam("amount") BigDecimal amount) {

        return ResponseEntity.ok(userService.creditFunds(userId, amount));
    }

}

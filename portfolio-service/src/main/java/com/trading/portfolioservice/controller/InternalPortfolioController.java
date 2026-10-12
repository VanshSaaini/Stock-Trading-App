package com.trading.portfolioservice.controller;

import com.trading.portfolioservice.repository.HoldingRepository;
import com.trading.portfolioservice.entity.Holding;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

/**
 * Service-to-service API (called by order-service before it accepts a SELL).
 * The api-gateway has no route for /internal/**, so this is not reachable from outside.
 */
@RestController
@RequestMapping("/internal/portfolio")
@RequiredArgsConstructor
public class InternalPortfolioController {

    private final HoldingRepository holdingRepository;

    /** How many shares of {symbol} does this user hold? (0 if none) */
    @GetMapping("/{userId}/holdings/{symbol}")
    public Map<String, Object> getHolding(
            @PathVariable("userId") String userId,
            @PathVariable("symbol") String symbol) {

        String normalized = symbol.toUpperCase(Locale.ROOT);

        int quantity = holdingRepository
                .findByUserIdAndSymbol(userId, normalized)
                .map(Holding::getQuantity)
                .orElse(0);

        return Map.of("symbol", normalized, "quantity", quantity);
    }

}

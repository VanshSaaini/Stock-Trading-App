package com.trading.portfolioservice.controller;

import com.trading.portfolioservice.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    /**
     *  Get my Portfolio with Live P & L
     *  (X-User-Id is set by the api-gateway from the verified JWT)
     *
     *  There is intentionally NO "get portfolio of any userId" endpoint here:
     *  everything under /api/v1/portfolio is reachable through the gateway,
     *  so it would let any logged-in user read another user's holdings.
     * */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyPortfolio(
            @RequestHeader("X-User-Id") String userId
    ){

        return ResponseEntity.ok(
                portfolioService.getPortfolio(userId)
        );

    }

}

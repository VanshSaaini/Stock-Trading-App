package com.trading.orderservice.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

import java.util.Map;

/** HTTP interface client for the INTERNAL holdings API of portfolio-service. */
public interface PortfolioClient {

    /** Response: {"symbol": "AAPL", "quantity": 12}  (quantity 0 when the user holds none) */
    @GetExchange("/internal/portfolio/{userId}/holdings/{symbol}")
    Map<String, Object> getHolding(@PathVariable("userId") String userId,
                                   @PathVariable("symbol") String symbol);

}

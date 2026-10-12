package com.trading.orderservice.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.PostExchange;

import java.math.BigDecimal;
import java.util.Map;

/**
 * HTTP interface client for the INTERNAL wallet API of user-service
 * (not exposed through the api-gateway).
 */
public interface UserServiceClient {

    @PostExchange("/internal/users/{userId}/funds/deduct")
    Map<String, Object> deductFunds(@PathVariable("userId") String userId,
                                    @RequestParam("amount") BigDecimal amount);

    @PostExchange("/internal/users/{userId}/funds/credit")
    Map<String, Object> creditFunds(@PathVariable("userId") String userId,
                                    @RequestParam("amount") BigDecimal amount);

}

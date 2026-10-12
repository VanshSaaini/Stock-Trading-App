package com.trading.orderservice.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

import java.util.Map;

/** HTTP interface client for market-data-service (created in HttpClientsConfig). */
public interface MarketDataClient {

    @GetExchange("/api/v1/market/stocks/{symbol}")
    Map<String, Object> getStockPrice(@PathVariable("symbol") String symbol);

}

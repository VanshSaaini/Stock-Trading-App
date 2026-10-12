package com.trading.marketdataservice.service;

import com.trading.marketdataservice.dto.StockPrice;
import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.repository.StockRespository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
@Order(2)
public class MarketDataService implements CommandLineRunner {

    private final StockRespository stockRespository;
    private final RedisTemplate<String, StockPrice> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final Random random = new Random();

    private static final String PRICE_KEY_PREFIX = "stock:price:";
    private static final String PRICE_UPDATED_TOPIC = "stock.price.updated";

    /**
     * In-memory source of truth for the live prices (Redis is only a cache).
     * ConcurrentHashMap - for thread safety.
     */
    private final Map<String, StockPrice> stockPrices = new ConcurrentHashMap<>();

    /**
     * Initialize stock prices once the application has started
     * (runs after DataInitializer, which has @Order(1)).
     */
    @Override
    public void run(String... args) {

        log.info("Loading Stock Data....");

        List<Stock> stocks = stockRespository.findByActiveTrue();

        if (stocks == null || stocks.isEmpty()) {
            log.warn("No active stocks found");
            return;
        }

        stocks.forEach(stock -> {
            StockPrice stockPrice = new StockPrice();
            stockPrice.setSymbol(stock.getSymbol());
            stockPrice.setCompanyName(stock.getCompanyName());
            stockPrice.setPrice(stock.getInitialPrice());
            stockPrice.setOpen(stock.getInitialPrice());
            stockPrice.setHigh(stock.getInitialPrice());
            stockPrice.setLow(stock.getInitialPrice());
            stockPrice.setChange(BigDecimal.ZERO);
            stockPrice.setChangePercent(BigDecimal.ZERO);
            stockPrice.setVolume(0L);
            stockPrice.setTimestamp(LocalDateTime.now());

            stockPrices.put(stock.getSymbol(), stockPrice);

            log.info("Loaded Stock: {} at {}", stock.getSymbol(), stock.getInitialPrice());
        });

        log.info("Initialized {} Stocks", stockPrices.size());
    }

    /**
     * Scheduled price update - every market.price-update-interval ms (2 seconds)
     */
    @Scheduled(fixedRateString = "${market.price-update-interval}")
    public void updatePrices() {

        if (stockPrices.isEmpty()) {
            log.debug("No stocks loaded yet");
            return;
        }

        stockPrices.forEach((stockSymbol, current) -> {

            BigDecimal oldPrice = current.getPrice();

            // random movement of up to +-0.25 % per tick
            double priceMovement = (random.nextDouble() - 0.5) * 0.5;
            BigDecimal priceChange = oldPrice
                    .multiply(BigDecimal.valueOf(priceMovement / 100))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newPrice = oldPrice
                    .add(priceChange)
                    .setScale(2, RoundingMode.HALF_UP);

            // prevent negative / zero price
            if (newPrice.compareTo(BigDecimal.ONE) < 0) {
                newPrice = BigDecimal.ONE.setScale(2);
            }

            // change vs the opening price
            BigDecimal changeAmount = newPrice
                    .subtract(current.getOpen())
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal changePercent = changeAmount
                    .divide(current.getOpen(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            // build a new object instead of mutating the one other threads may be reading
            StockPrice updated = new StockPrice(
                    current.getSymbol(),
                    current.getCompanyName(),
                    newPrice,
                    changeAmount,
                    changePercent,
                    current.getHigh().max(newPrice),
                    current.getLow().min(newPrice),
                    current.getOpen(),
                    current.getVolume(),
                    LocalDateTime.now()
            );

            stockPrices.put(stockSymbol, updated);

            // 1. Cache in Redis (failure must not stop the price feed)
            try {
                redisTemplate.opsForValue().set(PRICE_KEY_PREFIX + stockSymbol, updated);
            } catch (Exception e) {
                log.warn("Redis cache update failed for {}: {}", stockSymbol, e.getMessage());
            }

            // 2. Broadcast via web socket -> all subscribers of /topic/prices/{symbol}
            try {
                messagingTemplate.convertAndSend("/topic/prices/" + stockSymbol, updated);
            } catch (Exception e) {
                log.warn("WebSocket broadcast failed for {}: {}", stockSymbol, e.getMessage());
            }

            // 3. Publish to Kafka
            try {
                Map<String, Object> priceUpdatedEvent = new HashMap<>();
                priceUpdatedEvent.put("symbol", stockSymbol);
                priceUpdatedEvent.put("price", newPrice);
                priceUpdatedEvent.put("timestamp", updated.getTimestamp().toString());

                kafkaTemplate.send(PRICE_UPDATED_TOPIC, stockSymbol, priceUpdatedEvent);
            } catch (Exception e) {
                log.warn("Kafka publish failed for {}: {}", stockSymbol, e.getMessage());
            }
        });

        log.debug("Price updated for {} stocks", stockPrices.size());
    }

    /**
     * Get current price: Redis first, in-memory map as fallback
     */
    public StockPrice getPrice(String symbol) {

        String key = symbol.toUpperCase(Locale.ROOT);

        try {
            StockPrice cached = redisTemplate.opsForValue().get(PRICE_KEY_PREFIX + key);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for {}: {}", key, e.getMessage());
        }

        StockPrice price = stockPrices.get(key);

        if (price == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No stock found for symbol: " + key);
        }

        return price;
    }

    /**
     * Get all active stock prices
     */
    public List<StockPrice> getAllPrices() {
        return stockPrices.values().stream().toList();
    }

    /// Get all stocks
    public List<Stock> getAllStocks() {
        return stockRespository.findByActiveTrue();
    }
}

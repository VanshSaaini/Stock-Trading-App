package com.trading.orderservice.service;

import com.trading.orderservice.client.MarketDataClient;
import com.trading.orderservice.client.PortfolioClient;
import com.trading.orderservice.client.UserServiceClient;
import com.trading.orderservice.dto.OrderRequest;
import com.trading.orderservice.entity.Order;
import com.trading.orderservice.entity.OrderStatus;
import com.trading.orderservice.entity.OrderType;
import com.trading.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MarketDataClient marketDataClient;
    private final UserServiceClient userServiceClient;
    private final PortfolioClient portfolioClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ORDER_EXECUTED_TOPIC = "order.executed";
    private static final String ORDER_FAILED_TOPIC = "order.failed";

    /***
     *  Place a BUY/SELL order
     *
     *  FLOW:
     *  1: Get current price from market
     *  2: Calculate total amount
     *  3: Execute order (BUY: take money from wallet, SELL: check shares, then credit wallet)
     *  4: Publish order event to kafka
     *
     * */
    public Order placeOrder(OrderRequest orderRequest) {

        // "aapl" and "AAPL" must be the same stock everywhere (orders, portfolio, market data)
        String symbol = orderRequest.getSymbol().trim().toUpperCase(Locale.ROOT);

        log.info("Placing {} order: {} x {} for user: {}",
                orderRequest.getType(),
                orderRequest.getQuantity(),
                symbol,
                orderRequest.getUserId());

        // Step 1: current price (throws 404 for an unknown symbol, 503 if market service is down)
        BigDecimal currentPrice = fetchPrice(symbol);

        // Step 2:
        BigDecimal totalAmount = currentPrice
                .multiply(BigDecimal.valueOf(orderRequest.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        // Create Order
        Order order = new Order();
        order.setUserId(orderRequest.getUserId());
        order.setSymbol(symbol);
        order.setType(orderRequest.getType());
        order.setQuantity(orderRequest.getQuantity());
        order.setPrice(currentPrice);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING);

        Order savedOrder = orderRepository.save(order);

        log.info("Order Created: {}", savedOrder.getId());

        // Step 3: execute
        try{

            if(orderRequest.getType() == OrderType.BUY){
                userServiceClient.deductFunds(orderRequest.getUserId(), totalAmount);
            }
            else {
                // you can only sell what you own
                ensureSufficientShares(orderRequest.getUserId(), symbol, orderRequest.getQuantity());
                userServiceClient.creditFunds(orderRequest.getUserId(), totalAmount);
            }

            savedOrder.setStatus(OrderStatus.EXECUTED);
            savedOrder.setExecutedAt(LocalDateTime.now());
            orderRepository.save(savedOrder);

            log.info("Order EXECUTED: {} {} x {} at {}",
                    savedOrder.getType(),savedOrder.getQuantity(),
                    savedOrder.getSymbol(), currentPrice);

        } catch (Exception e){
            String reason = describeFailure(orderRequest.getType(), e);

            savedOrder.setStatus(OrderStatus.FAILED);
            savedOrder.setFailureReason(reason);
            orderRepository.save(savedOrder);

            log.error("Order FAILED: {} reason: {}", savedOrder.getId(), reason);
            publishOrderEvent(ORDER_FAILED_TOPIC, savedOrder, reason);

            return savedOrder;
        }

        // Step 4: publish OUTSIDE the try block - if Kafka is down the money has already moved,
        // so the order must stay EXECUTED instead of being marked FAILED.
        publishOrderEvent(ORDER_EXECUTED_TOPIC, savedOrder, null);

        return savedOrder;

    }

    private BigDecimal fetchPrice(String symbol) {
        try {
            Map<String, Object> priceData = marketDataClient.getStockPrice(symbol);

            Object price = priceData == null ? null : priceData.get("price");
            if (price == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Market data service returned no price for " + symbol);
            }
            return new BigDecimal(price.toString());

        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown stock symbol: " + symbol);
        } catch (RestClientException e) {
            log.error("Market data call failed for {}: {}", symbol, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Market data service is unavailable, please try again");
        }
    }

    private void ensureSufficientShares(String userId, String symbol, int quantity) {
        Map<String, Object> holding = portfolioClient.getHolding(userId, symbol);

        int owned = 0;
        if (holding != null && holding.get("quantity") != null) {
            owned = Integer.parseInt(holding.get("quantity").toString());
        }

        if (owned < quantity) {
            throw new IllegalStateException(
                    "Insufficient shares: you own " + owned + " of " + symbol);
        }
    }

    /** Short, readable reason that fits the failureReason column */
    private String describeFailure(OrderType type, Exception e) {
        String reason;

        if (e instanceof HttpClientErrorException) {
            HttpClientErrorException http = (HttpClientErrorException) e;
            if (type == OrderType.BUY && http.getStatusCode().value() == 400) {
                reason = "Insufficient funds";
            } else {
                reason = "Rejected by downstream service (HTTP " + http.getStatusCode().value() + ")";
            }
        } else if (e instanceof ResourceAccessException) {
            reason = "A required service is unavailable, please try again";
        } else {
            reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }

        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    private void publishOrderEvent(String topic,
                                   Order order,
                                   String reason){

        Map<String, Object> orderEvent = new HashMap<>();

        orderEvent.put("orderId",order.getId());
        orderEvent.put("userId",order.getUserId());
        orderEvent.put("symbol",order.getSymbol());
        orderEvent.put("type",order.getType().name());
        orderEvent.put("quantity",order.getQuantity());
        orderEvent.put("price",order.getPrice());
        orderEvent.put("totalAmount",order.getTotalAmount());
        orderEvent.put("status",order.getStatus().name());
        if(reason != null) orderEvent.put("reason",reason);

        try {
            // Key = userId (NOT orderId): Kafka only guarantees ordering inside one partition,
            // and a user's BUY must be processed before their later SELL.
            kafkaTemplate.send(topic, order.getUserId(), orderEvent)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish {} for order {}: {}",
                                    topic, order.getId(), ex.getMessage());
                        } else {
                            log.info("Event Published: {} for Order: {}", topic, order.getId());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to publish {} for order {}: {}", topic, order.getId(), e.getMessage());
        }

    }

    public Order getOrder(String orderId) {

        return orderRepository.findById(orderId)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Order Not Found: " + orderId)
                );

    }

    public List<Order> getMyOrders(String userId) {

        return orderRepository
                .findByUserIdOrderByCreatedAtDesc(userId);

    }

}

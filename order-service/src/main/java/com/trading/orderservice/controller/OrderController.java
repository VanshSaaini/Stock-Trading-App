package com.trading.orderservice.controller;

import com.trading.orderservice.dto.OrderRequest;
import com.trading.orderservice.entity.Order;
import com.trading.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     *  place buy/sell order
     * */
    @PostMapping
    public ResponseEntity<Order> placeOrder(@RequestHeader("X-User-Id") String userId,
                                            @Valid @RequestBody OrderRequest orderRequest){

        orderRequest.setUserId(userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(orderRequest));

    }

    @GetMapping("/my-orders")
    public ResponseEntity<List<Order>> getMyOrders(
            @RequestHeader("X-User-Id") String userId
    ){
        return ResponseEntity.ok(
                orderService.getMyOrders(userId)
        );
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(
            @PathVariable("orderId") String orderId,
            @RequestHeader("X-User-Id") String userId){

        Order order = orderService.getOrder(orderId);

        // Security Check - users may only read their own orders
        if(!order.getUserId().equals(userId)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This order belongs to another user");
        }

        return ResponseEntity.ok(order);

    }

}

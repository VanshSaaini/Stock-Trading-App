package com.trading.orderservice.dto;

import com.trading.orderservice.entity.OrderType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {

    // set by the controller from the X-User-Id header, never trusted from the body
    private String userId;

    @NotBlank(message="Stock Symbol is required")
    private String symbol;

    // @NotBlank only works on text; an enum needs @NotNull
    @NotNull(message="Order Type required - BUY or SELL")
    private OrderType type;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;

}

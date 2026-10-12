package com.trading.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private BigDecimal walletBalance;

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";

}

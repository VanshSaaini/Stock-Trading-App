package com.trading.userservice.dto;

import com.trading.userservice.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {

    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private BigDecimal walletBalance;
    private UserStatus status;
    private LocalDateTime createdAt;

}

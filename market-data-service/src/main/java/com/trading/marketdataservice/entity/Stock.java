package com.trading.marketdataservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name="stocks")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Stock {

    @Id
    @Column(nullable=false, unique=true)
    private String symbol;

    @Column(nullable=false, unique=true)
    private String companyName;

    @Column(nullable=false, precision = 15, scale=2)
    private BigDecimal initialPrice;

    @Column(nullable=false)
    private String exchange;

    @Column(nullable=false)
    private String currency;

    private boolean active = true;

}

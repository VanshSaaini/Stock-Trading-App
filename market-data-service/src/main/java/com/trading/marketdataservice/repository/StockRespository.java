package com.trading.marketdataservice.repository;

import com.trading.marketdataservice.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockRespository extends JpaRepository<Stock, String> {
    List<Stock> findByActiveTrue();
}

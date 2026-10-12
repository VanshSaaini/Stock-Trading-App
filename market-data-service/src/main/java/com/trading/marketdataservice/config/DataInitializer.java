package com.trading.marketdataservice.config;

import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.repository.StockRespository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private final StockRespository stockRespository;

    @Override
    public void run(String... args) throws Exception {

        if (stockRespository.count() > 0) {
            log.info("Stocks already Initialized - skipping");
            return;
        }

        log.info("Initializing Stock Data...");

        List<Stock> stocks = List.of(
                // US Stocks
                new Stock("AAPL","Apple Inc",
                        BigDecimal.valueOf(189.50),"NASDAQ",
                        "USD",true),
                new Stock("GOOGL","Alphabet Inc",
                        BigDecimal.valueOf(141.80),"NASDAQ",
                        "USD",true),
                new Stock("MSFT","Microsoft Corp",
                        BigDecimal.valueOf(178.90),"NASDAQ",
                        "USD",true),

                // INDIAN Stocks
                new Stock("TCS","Tata Consultancy Services",
                        BigDecimal.valueOf(1129.50),"NSE",
                        "INR",true),
                new Stock("INFY","Infosys",
                        BigDecimal.valueOf(2222.50),"NSE",
                        "INR",true),
                new Stock("RELIANCE","Reliance Industries",
                        BigDecimal.valueOf(3182.30),"NSE",
                        "INR",true)
        );

        stockRespository.saveAll(stocks);
        log.info("Initialized {} Stocks successfully", stocks.size());
    }
}

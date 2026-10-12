package com.trading.portfolioservice.service;

import com.trading.portfolioservice.entity.Holding;
import com.trading.portfolioservice.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioEventConsumer {

    private final HoldingRepository holdingRepository;

    @KafkaListener(topics = "order.executed")
    public void consumeOrderExecuted(
            @Payload Map<String, Object> payload){

        try{
            String userId = (String) payload.get("userId");
            String symbol = (String) payload.get("symbol");
            String type = (String) payload.get("type");

            int quantity = Integer.parseInt(
                    payload.get("quantity").toString()
            );

            BigDecimal price = new BigDecimal(
                    payload.get("price").toString()
            );

            log.info("Updating portfolio - user: {} {} {} x {} at {}", userId, type, symbol, quantity, price);

            if("BUY".equals(type)){
                addHolding(userId, symbol, quantity, price);
            } else if("SELL".equals(type)){
                removeHolding(userId, symbol, quantity);
            }

        } catch(Exception e){
            // log the full stack trace, not just the message
            log.error("Error updating portfolio for payload {}", payload, e);
        }

    }

    /***
     *  Add or Update after BUY Order
     *  Calculates new average buy Price
     * */
    public void addHolding(String userId, String symbol, int quantity, BigDecimal price){

        BigDecimal boughtAmount = price.multiply(BigDecimal.valueOf(quantity));

        Holding holding = holdingRepository
                .findByUserIdAndSymbol(userId, symbol)
                .orElse(null);

        if(holding == null){
            // First time Buying this Stock
            holding = new Holding();
            holding.setUserId(userId);
            holding.setSymbol(symbol);
            holding.setCompanyName(symbol);
            holding.setQuantity(quantity);
            holding.setAverageBuyPrice(price);
            holding.setTotalInvested(boughtAmount);
        } else{
            // Already holding this Stock - Update average price
            // new invested = old invested + (price paid NOW x shares bought NOW)
            int newQuantity = holding.getQuantity() + quantity;
            BigDecimal newInvested = holding.getTotalInvested().add(boughtAmount);

            BigDecimal newAvgPrice = newInvested
                    .divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);

            holding.setQuantity(newQuantity);
            holding.setTotalInvested(newInvested);
            holding.setAverageBuyPrice(newAvgPrice);

        }
        holdingRepository.save(holding);
        log.info("Holding updated - user: {} {} x {} at {}", userId, symbol, quantity, price);
    }

    public void removeHolding(String userId, String symbol, int quantity){

        Holding holding = holdingRepository
                .findByUserIdAndSymbol(userId, symbol)
                .orElseThrow(
                        () -> new RuntimeException("Holding not Found: " + symbol)
                );

        if(holding.getQuantity() < quantity){
            throw new RuntimeException("Insufficient Shares to SELL");
        }

        int newQuantity = holding.getQuantity() - quantity;

        if(newQuantity == 0){
            holdingRepository.delete(holding);
            log.info("Holding Removed: {} Sold all {} shares",userId, symbol);
        } else{
            BigDecimal newInvested = holding.getAverageBuyPrice()
                    .multiply(BigDecimal.valueOf(newQuantity));

            holding.setQuantity(newQuantity);
            holding.setTotalInvested(newInvested);

            holdingRepository.save(holding);
            log.info("Holding reduced: {} now has {} shares of {}",userId, newQuantity, symbol);
        }

    }

}

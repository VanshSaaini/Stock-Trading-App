package com.trading.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    @KafkaListener(topics = "order.executed")
    public void consumeOrderExecuted(
            @Payload Map<String, Object> payload){

        try{
            String userId = (String) payload.get("userId");
            String type = (String) payload.get("type");
            String symbol = (String) payload.get("symbol");
            Object quantity = payload.get("quantity");
            Object price = payload.get("price");
            Object totalAmount = payload.get("totalAmount");

            sendNotification(
                    userId, "BUY".equals(type) ? "ORDER EXECUTED - BUY" : "ORDER EXECUTED - SELL",
                    String.format("%s %s shares of %s at %s. Total: %s", type, quantity, symbol, price, totalAmount)
            );

        } catch(Exception e){
            log.error("Error handling order.executed event", e);
        }

    }

    @KafkaListener(topics = "order.failed")
    public void consumeOrderFailed(
            @Payload Map<String, Object> payload){
        try{
            String userId = (String) payload.get("userId");
            String symbol = (String) payload.get("symbol");
            String reason = (String) payload.get("reason");

            sendNotification(userId, "ORDER FAILED", String.format("Your order for %s failed. Reason: %s", symbol, reason));

        } catch(Exception e){
            log.error("Error handling order.failed event", e);
        }
    }

    @KafkaListener(topics = "user.registered")
    public void consumeUserRegistered(
            @Payload Map<String, Object> payload
    ){

        try{
            String userId = (String) payload.get("userId");
            String firstName = (String) payload.get("firstName");
            Object walletBalance = payload.get("walletBalance");

            sendNotification(userId, "Welcome to the Stock Trading Platform", String.format("Welcome %s! %s added to your wallet.", firstName, walletBalance));

        } catch(Exception e){
            log.error("Error handling user.registered event", e);
        }

    }

    public void sendNotification(String userId, String title, String message){

        // every {} needs an argument - the old code had none, so userId/title/message were never printed
        log.info("-----------------------------------------------------------");
        log.info("NOTIFICATION SENT");
        log.info("TO USER ID: {}", userId);
        log.info("TITLE: {}", title);
        log.info("MESSAGE: {}", message);
        log.info("-----------------------------------------------------------");
    }

}

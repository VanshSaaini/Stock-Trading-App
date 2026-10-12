package com.trading.marketdataservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * How it works
 * 1: client connects to /ws endpoint
 * 2: client subscribs to /topic/prices/{symbol}
 * 3: Server publishes price updates to that topic
 * 4: Client receives updates in real-time - no polling needed
 *
 * this is exactly how zerodha/ robinhood/Groww  stream real time prices
 * */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // topics prefixed with /topic -> broadcast to all subscribers
        registry.enableSimpleBroker("/topic");

        // application destination prefix
        // client messages to /app/** are routed to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");

    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        // websocket endpoints - clients connect here
        // SockJS fallback for browsers that don't support WebSocket
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS();

    }

}

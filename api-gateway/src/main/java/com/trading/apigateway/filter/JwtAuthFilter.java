package com.trading.api_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

/**
 * Validates the JWT access token and forwards the caller's id to the
 * downstream service in the X-User-Id header.
 *
 * Used per-route in application.yaml (filter name: JwtAuthFilter).
 * Public routes (register / login / websocket) simply do not use this filter.
 */
@Slf4j
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final SecretKey signingKey;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        super(Config.class);
        // fails fast at startup if the secret is not valid Base64 or is shorter than 256 bits
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || authHeader.isBlank()) {
                log.warn("Missing Authorization header: {}", path);
                return unauthorized(exchange);
            }

            if (!authHeader.startsWith("Bearer ") || authHeader.length() <= 7) {
                log.warn("Invalid Authorization format: {}", path);
                return unauthorized(exchange);
            }

            String token = authHeader.substring(7).trim();

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // refresh tokens must not be accepted as access tokens
                if (!"access".equals(claims.get("type", String.class))) {
                    log.warn("Token is not an access token: {}", path);
                    return unauthorized(exchange);
                }

                String userId = claims.get("userId", String.class);
                if (userId == null || userId.isBlank()) {
                    log.warn("Token has no userId claim: {}", path);
                    return unauthorized(exchange);
                }

                // header() replaces any X-User-Id the client tried to send
                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("X-User-Id", userId)
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());

            } catch (JwtException | IllegalArgumentException e) {
                log.warn("Invalid or expired JWT: {}", e.getMessage());
                return unauthorized(exchange);
            }
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
    }

}

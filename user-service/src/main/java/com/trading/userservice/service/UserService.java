package com.trading.userservice.service;

import com.trading.userservice.dto.AuthResponse;
import com.trading.userservice.dto.LoginRequest;
import com.trading.userservice.dto.RegisterRequest;
import com.trading.userservice.dto.UserResponse;
import com.trading.userservice.entity.User;
import com.trading.userservice.entity.UserStatus;
import com.trading.userservice.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String USER_REGISTERED_TOPIC = "user.registered";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${wallet.initial-balance:10000}")
    private BigDecimal initialBalance;

    private SecretKey signingKey;

    @PostConstruct
    void initSigningKey() {
        // fails fast at startup if the secret is not valid Base64 or is shorter than 256 bits
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public AuthResponse register(RegisterRequest request) {

        String email = normalizeEmail(request.getEmail());
        log.info("Register request: {}", email);

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // the starting balance is decided by the server, never by the client
        user.setWalletBalance(initialBalance);

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // two registrations with the same email raced past existsByEmail
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        log.info("User registered: {}", savedUser.getId());

        // publish user.registered event
        Map<String, Object> userRegisteredEvent = new HashMap<>();
        userRegisteredEvent.put("userId", savedUser.getId());
        userRegisteredEvent.put("email", savedUser.getEmail());
        userRegisteredEvent.put("firstName", savedUser.getFirstName());
        userRegisteredEvent.put("lastName", savedUser.getLastName());
        userRegisteredEvent.put("walletBalance", savedUser.getWalletBalance());

        try {
            kafkaTemplate.send(USER_REGISTERED_TOPIC, savedUser.getId(), userRegisteredEvent);
        } catch (Exception e) {
            // registration must not fail just because the notification event could not be sent
            log.error("Could not publish {} event: {}", USER_REGISTERED_TOPIC, e.getMessage());
        }

        return buildAuthResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request) {

        String email = normalizeEmail(request.getEmail());
        log.info("Login attempt: {}", email);

        // same message for "unknown email" and "wrong password" so accounts can't be enumerated
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is " + user.getStatus());
        }

        log.info("Login successful: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    public UserResponse getUserById(String userId) {
        return mapToResponse(findUser(userId));
    }

    /** Wallet top-up (increase balance) */
    @Transactional
    public UserResponse addFunds(String userId, BigDecimal amount) {

        BigDecimal value = requirePositive(amount);
        User user = findUserForUpdate(userId);

        user.setWalletBalance(user.getWalletBalance().add(value));
        User savedUser = userRepository.save(user);

        log.info("Funds added: {} to user {}", value, userId);

        return mapToResponse(savedUser);
    }

    /** Used by order-service when a BUY order executes */
    @Transactional
    public UserResponse deductFunds(String userId, BigDecimal amount) {

        BigDecimal value = requirePositive(amount);
        User user = findUserForUpdate(userId);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is " + user.getStatus());
        }

        if (user.getWalletBalance().compareTo(value) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough funds");
        }

        user.setWalletBalance(user.getWalletBalance().subtract(value));
        User savedUser = userRepository.save(user);

        log.info("Funds deducted: {} from user {}", value, userId);

        return mapToResponse(savedUser);
    }

    /** Used by order-service when a SELL order executes */
    @Transactional
    public UserResponse creditFunds(String userId, BigDecimal amount) {

        BigDecimal value = requirePositive(amount);
        User user = findUserForUpdate(userId);

        user.setWalletBalance(user.getWalletBalance().add(value));
        User savedUser = userRepository.save(user);

        log.info("Funds credited: {} to user {}", value, userId);

        return mapToResponse(savedUser);
    }

    // ------------------------------------------------------------------ helpers

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + userId));
    }

    private User findUserForUpdate(String userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + userId));
    }

    private AuthResponse buildAuthResponse(User user) {

        AuthResponse authResponse = new AuthResponse();

        authResponse.setUserId(user.getId());
        authResponse.setEmail(user.getEmail());
        authResponse.setFirstName(user.getFirstName());
        authResponse.setLastName(user.getLastName());
        authResponse.setWalletBalance(user.getWalletBalance());
        authResponse.setAccessToken(generateToken(user.getId(), user.getEmail(), "access", jwtExpiration));
        authResponse.setRefreshToken(generateToken(user.getId(), user.getId(), "refresh", refreshExpiration));

        return authResponse;
    }

    /**
     * "type" claim lets the gateway accept only access tokens
     * (a refresh token must not work as an access token).
     */
    private String generateToken(String userId, String subject, String type, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("userId", userId)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(signingKey)
                .compact();
    }

    private UserResponse mapToResponse(User user) {

        UserResponse userResponse = new UserResponse();

        userResponse.setId(user.getId());
        userResponse.setEmail(user.getEmail());
        userResponse.setFirstName(user.getFirstName());
        userResponse.setLastName(user.getLastName());
        userResponse.setWalletBalance(user.getWalletBalance());
        userResponse.setStatus(user.getStatus());
        userResponse.setCreatedAt(user.getCreatedAt());

        return userResponse;
    }
}

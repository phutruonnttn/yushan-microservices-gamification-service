package com.yushan.gamification_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yushan.gamification_service.dto.event.EventEnvelope;
import com.yushan.gamification_service.dto.event.UserLoggedInEvent;
import com.yushan.gamification_service.dto.event.UserRegisteredEvent;
import com.yushan.gamification_service.service.GamificationService;
import com.yushan.gamification_service.service.IdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
public class UserEventListener {

    @Autowired
    private GamificationService gamificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdempotencyService idempotencyService;

    private static final String IDEMPOTENCY_PREFIX_REGISTRATION = "idempotency:user-registration:";
    private static final String IDEMPOTENCY_PREFIX_LOGIN = "idempotency:user-login:";

    @KafkaListener(topics = "user.events", groupId = "gamification-service")
    public void handleUserEvent(String message) {
        try {
            EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);

            switch (envelope.eventType()) {
                case "UserRegisteredEvent":
                    UserRegisteredEvent registeredEvent = objectMapper.treeToValue(envelope.payload(), UserRegisteredEvent.class);
                    UUID userId = registeredEvent.uuid();
                    
                    // Idempotency check: registration should only happen once per user (hybrid: Redis + Database)
                    String registrationKey = IDEMPOTENCY_PREFIX_REGISTRATION + userId;
                    if (idempotencyService.isProcessed(registrationKey, "UserRegistration")) {
                        log.info("UserRegistrationEvent already processed, skipping: userId={}", userId);
                        return;
                    }
                    
                    log.info("Processing UserRegisteredEvent for email: {}", registeredEvent.email());
                    gamificationService.processUserRegistration(userId);
                    
                    // Mark as processed (both Redis and Database)
                    idempotencyService.markAsProcessed(registrationKey, "UserRegistration");
                    log.info("Successfully processed UserRegistrationEvent for userId: {}", userId);
                    break;

                case "UserLoggedInEvent":
                    UserLoggedInEvent loggedInEvent = objectMapper.treeToValue(envelope.payload(), UserLoggedInEvent.class);
                    UUID loginUserId = loggedInEvent.uuid();
                    
                    // Idempotency check: daily login reward should only be processed once per day (hybrid: Redis + Database)
                    // Use userId + date as key (processUserLogin already checks if reward was claimed today)
                    LocalDate today = LocalDate.now();
                    String loginKey = IDEMPOTENCY_PREFIX_LOGIN + loginUserId + ":" + today;
                    if (idempotencyService.isProcessed(loginKey, "UserLogin")) {
                        log.info("UserLoggedInEvent already processed for today, skipping: userId={}, date={}", loginUserId, today);
                        return;
                    }
                    
                    log.info("Processing UserLoggedInEvent for email: {}", loggedInEvent.email());
                    gamificationService.processUserLogin(loginUserId);
                    
                    // Mark as processed (both Redis and Database)
                    idempotencyService.markAsProcessed(loginKey, "UserLogin");
                    log.info("Successfully processed UserLoggedInEvent for userId: {}", loginUserId);
                    break;

                default:
                    log.warn("Received unknown event type: {}", envelope.eventType());
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to process event message: {}", message, e);
            // Re-throw to trigger Kafka retry mechanism
            throw new RuntimeException("Failed to process user event", e);
        }
    }
}
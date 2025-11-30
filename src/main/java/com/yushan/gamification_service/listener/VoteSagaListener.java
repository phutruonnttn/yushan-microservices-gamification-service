package com.yushan.gamification_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yushan.gamification_service.dto.event.*;
import com.yushan.gamification_service.entity.YuanReservation;
import com.yushan.gamification_service.repository.UserProgressRepository;
import com.yushan.gamification_service.service.GamificationService;
import com.yushan.gamification_service.service.IdempotencyService;
import com.yushan.gamification_service.service.YuanReservationService;
import com.yushan.gamification_service.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vote SAGA Listener for Gamification Service
 * Handles vote creation SAGA steps in Choreography pattern
 */
@Slf4j
@Component
public class VoteSagaListener {

    @Autowired
    private YuanReservationService yuanReservationService;

    @Autowired
    private GamificationService gamificationService;

    @Autowired
    private UserProgressRepository userProgressRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdempotencyService idempotencyService;

    @Value("${gamification.rewards.vote-exp:3}")
    private double voteExp;

    private static final String SAGA_TOPIC_START = "vote-saga.start";
    private static final String SAGA_TOPIC_YUAN_RESERVED = "vote-saga.yuan-reserved";
    private static final String SAGA_TOPIC_VOTE_CREATED = "vote-saga.vote-created";
    private static final String SAGA_TOPIC_FAILED = "vote-saga.failed";
    private static final String SAGA_TOPIC_COMPENSATE = "vote-saga.compensate-yuan";

    private static final String IDEMPOTENCY_PREFIX_SAGA_START = "idempotency:vote-saga-start:";
    private static final String IDEMPOTENCY_PREFIX_SAGA_COMPENSATE = "idempotency:vote-saga-compensate:";

    /**
     * Step 1: Start SAGA - Reserve Yuan
     * Listens to vote-saga.start topic
     */
    @KafkaListener(topics = SAGA_TOPIC_START, groupId = "gamification-service-vote-saga")
    public void handleVoteSagaStart(@Payload String eventJson) {
        try {
            log.info("Received VoteSagaStartEvent: {}", eventJson);
            
            VoteSagaStartEvent event = objectMapper.readValue(eventJson, VoteSagaStartEvent.class);
            
            // Idempotency check
            String idempotencyKey = IDEMPOTENCY_PREFIX_SAGA_START + event.getSagaId();
            if (idempotencyService.isProcessed(idempotencyKey, "VoteSagaStart")) {
                log.info("VoteSagaStartEvent already processed, skipping: sagaId={}", event.getSagaId());
                return;
            }

            // Check for pending reservations
            YuanReservation pendingReservation = yuanReservationService.getReservationBySagaId(event.getSagaId());
            if (pendingReservation != null && 
                pendingReservation.getStatus() == YuanReservation.ReservationStatus.RESERVED) {
                log.warn("Reservation already exists for SAGA: {}", event.getSagaId());
                // Continue with existing reservation
                publishYuanReservedEvent(event, pendingReservation.getReservationId());
                idempotencyService.markAsProcessed(idempotencyKey, "VoteSagaStart");
                return;
            }

            // Reserve Yuan
            UUID reservationId = yuanReservationService.reserveYuan(
                event.getUserId(),
                1.0, // 1 Yuan per vote
                event.getSagaId()
            );

            // Publish Yuan reserved event
            publishYuanReservedEvent(event, reservationId);
            
            // Mark as processed
            idempotencyService.markAsProcessed(idempotencyKey, "VoteSagaStart");
            
            log.info("Successfully processed VoteSagaStartEvent: sagaId={}, reservationId={}", 
                    event.getSagaId(), reservationId);
                    
        } catch (ValidationException e) {
            log.error("Validation failed for VoteSagaStartEvent: {}", eventJson, e);
            handleSagaFailure(eventJson, e.getMessage());
        } catch (Exception e) {
            log.error("Error processing VoteSagaStartEvent: {}", eventJson, e);
            handleSagaFailure(eventJson, "Failed to reserve Yuan: " + e.getMessage());
        }
    }

    /**
     * Step 3: Confirm & Finalize - After vote is created
     * Listens to vote-saga.vote-created topic
     */
    @KafkaListener(topics = SAGA_TOPIC_VOTE_CREATED, groupId = "gamification-service-vote-saga")
    public void handleVoteSagaVoteCreated(@Payload String eventJson) {
        VoteSagaVoteCreatedEvent event = null;
        try {
            log.info("Received VoteSagaVoteCreatedEvent: {}", eventJson);
            
            // Parse event - handle case where JSON might be double-encoded or escaped
            String jsonToParse = eventJson;
            if (jsonToParse.startsWith("\"") && jsonToParse.endsWith("\"")) {
                // Remove outer quotes if double-encoded
                jsonToParse = jsonToParse.substring(1, jsonToParse.length() - 1).replace("\\\"", "\"");
            }
            
            // Parse event first
            event = objectMapper.readValue(jsonToParse, VoteSagaVoteCreatedEvent.class);
            
            // Idempotency check
            String idempotencyKey = "idempotency:vote-saga-confirm:" + event.getSagaId();
            if (idempotencyService.isProcessed(idempotencyKey, "VoteSagaConfirm")) {
                log.info("VoteSagaVoteCreatedEvent already processed, skipping: sagaId={}", event.getSagaId());
                return;
            }

            // Confirm reservation (convert to actual deduction)
            // This method will check balance, create YuanTransaction, and mark reservation as CONFIRMED
            yuanReservationService.confirmReservation(event.getReservationId(), event.getUserId(), userProgressRepository);
            
            // Award EXP for voting (without deducting Yuan - already done in confirmReservation)
            gamificationService.awardExpForVote(event.getUserId());
            
            // Mark as processed
            idempotencyService.markAsProcessed(idempotencyKey, "VoteSagaConfirm");
            
            log.info("Successfully confirmed Yuan deduction and awarded EXP: sagaId={}, userId={}", 
                    event.getSagaId(), event.getUserId());
                    
        } catch (Exception e) {
            log.error("Error processing VoteSagaVoteCreatedEvent: {}", eventJson, e);
            // If confirmation fails, trigger compensation and publish failure event
            if (event != null) {
                // Use parsed event object - we have all info needed
                String reason = "Failed to confirm Yuan deduction: " + e.getMessage();
                
                // Publish compensation event (release Yuan)
                handleSagaCompensation(event.getSagaId(), event.getUserId(), event.getReservationId(), reason);
                
                // Publish failure event (so Engagement Service can delete vote)
                VoteSagaFailedEvent failedEvent = VoteSagaFailedEvent.builder()
                        .sagaId(event.getSagaId())
                        .userId(event.getUserId())
                        .novelId(event.getNovelId())
                        .reservationId(event.getReservationId())
                        .reason(reason)
                        .timestamp(LocalDateTime.now())
                        .build();
                
                kafkaTemplate.send(SAGA_TOPIC_FAILED, event.getSagaId(), failedEvent);
                log.info("Published VoteSagaFailedEvent for compensation: sagaId={}, novelId={}", 
                        event.getSagaId(), event.getNovelId());
            } else {
                // If we can't parse event, get reservation info from database using sagaId
                try {
                    // Try to extract sagaId from JSON string
                    if (eventJson.contains("sagaId")) {
                        String sagaId = extractSagaIdFromJson(eventJson);
                        if (sagaId != null) {
                            YuanReservation reservation = yuanReservationService.getReservationBySagaId(sagaId);
                            if (reservation != null) {
                                String reason = "Failed to confirm Yuan deduction: " + e.getMessage();
                                handleSagaCompensation(sagaId, reservation.getUserId(), reservation.getReservationId(), reason);
                                log.warn("Published compensation event but VoteSagaFailedEvent cannot be published without novelId: sagaId={}", sagaId);
                            } else {
                                log.error("Could not find reservation for sagaId: {}", sagaId);
                            }
                        }
                    }
                } catch (Exception parseEx) {
                    log.error("Failed to extract sagaId from event JSON for compensation", parseEx);
                }
            }
        }
    }
    
    /**
     * Extract sagaId from JSON string
     */
    private String extractSagaIdFromJson(String json) {
        try {
            // Simple JSON parsing to extract sagaId
            int sagaIdStart = json.indexOf("\"sagaId\":\"") + 10;
            if (sagaIdStart > 9) {
                int sagaIdEnd = json.indexOf("\"", sagaIdStart);
                if (sagaIdEnd > sagaIdStart) {
                    return json.substring(sagaIdStart, sagaIdEnd);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract sagaId from JSON string", e);
        }
        return null;
    }

    /**
     * Compensation Handler: Release Yuan reservation
     * Listens to vote-saga.compensate-yuan topic
     */
    @KafkaListener(topics = SAGA_TOPIC_COMPENSATE, groupId = "gamification-service-vote-saga")
    public void handleVoteSagaCompensation(@Payload String eventJson) {
        try {
            log.info("Received VoteSagaCompensateYuanEvent: {}", eventJson);
            
            VoteSagaCompensateYuanEvent event = objectMapper.readValue(eventJson, VoteSagaCompensateYuanEvent.class);
            
            // Idempotency check
            String idempotencyKey = IDEMPOTENCY_PREFIX_SAGA_COMPENSATE + event.getSagaId();
            if (idempotencyService.isProcessed(idempotencyKey, "VoteSagaCompensate")) {
                log.info("VoteSagaCompensateYuanEvent already processed, skipping: sagaId={}", event.getSagaId());
                return;
            }

            // Release reservation (rollback)
            boolean released = yuanReservationService.releaseReservation(
                event.getReservationId(),
                event.getUserId()
            );
            
            if (released) {
                log.info("Successfully released Yuan reservation: sagaId={}, reservationId={}", 
                        event.getSagaId(), event.getReservationId());
            } else {
                log.warn("Failed to release Yuan reservation (may already be processed): sagaId={}, reservationId={}", 
                        event.getSagaId(), event.getReservationId());
            }
            
            // Mark as processed
            idempotencyService.markAsProcessed(idempotencyKey, "VoteSagaCompensate");
            
        } catch (Exception e) {
            log.error("Error processing VoteSagaCompensateYuanEvent: {}", eventJson, e);
            // Don't throw - compensation should be idempotent
        }
    }

    /**
     * Publish Yuan reserved event
     */
    private void publishYuanReservedEvent(VoteSagaStartEvent startEvent, UUID reservationId) {
        try {
            VoteSagaYuanReservedEvent event = VoteSagaYuanReservedEvent.builder()
                    .sagaId(startEvent.getSagaId())
                    .userId(startEvent.getUserId())
                    .novelId(startEvent.getNovelId())
                    .reservationId(reservationId)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            // Convert to JSON string for consistency with other listeners
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(SAGA_TOPIC_YUAN_RESERVED, startEvent.getSagaId(), eventJson);
            log.info("Published VoteSagaYuanReservedEvent: sagaId={}, reservationId={}", 
                    startEvent.getSagaId(), reservationId);
        } catch (Exception e) {
            log.error("Failed to publish VoteSagaYuanReservedEvent: sagaId={}", startEvent.getSagaId(), e);
            throw new RuntimeException("Failed to publish Yuan reserved event", e);
        }
    }

    /**
     * Handle SAGA failure
     */
    private void handleSagaFailure(String eventJson, String reason) {
        try {
            VoteSagaStartEvent event = objectMapper.readValue(eventJson, VoteSagaStartEvent.class);
            
            VoteSagaFailedEvent failedEvent = VoteSagaFailedEvent.builder()
                    .sagaId(event.getSagaId())
                    .userId(event.getUserId())
                    .novelId(event.getNovelId())
                    .reason(reason)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            kafkaTemplate.send(SAGA_TOPIC_FAILED, event.getSagaId(), failedEvent);
            log.info("Published VoteSagaFailedEvent: sagaId={}, reason={}", event.getSagaId(), reason);
        } catch (Exception e) {
            log.error("Failed to publish VoteSagaFailedEvent", e);
        }
    }

    /**
     * Handle SAGA compensation
     * Gets reservation info from database using sagaId to avoid parsing failed event JSON
     */
    private void handleSagaCompensation(String sagaId, UUID userId, UUID reservationId, String reason) {
        try {
            VoteSagaCompensateYuanEvent compensateEvent = VoteSagaCompensateYuanEvent.builder()
                    .sagaId(sagaId)
                    .userId(userId)
                    .reservationId(reservationId)
                    .reason(reason)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            kafkaTemplate.send(SAGA_TOPIC_COMPENSATE, sagaId, compensateEvent);
            log.info("Published VoteSagaCompensateYuanEvent: sagaId={}, reason={}", sagaId, reason);
        } catch (Exception e) {
            log.error("Failed to publish VoteSagaCompensateYuanEvent: sagaId={}", sagaId, e);
        }
    }
}


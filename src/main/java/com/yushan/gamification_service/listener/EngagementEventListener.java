package com.yushan.gamification_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yushan.gamification_service.service.GamificationService;
import com.yushan.gamification_service.service.IdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class EngagementEventListener {

    @Autowired
    private GamificationService gamificationService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdempotencyService idempotencyService;

    private static final String IDEMPOTENCY_PREFIX_COMMENT = "idempotency:comment-reward:";
    private static final String IDEMPOTENCY_PREFIX_REVIEW = "idempotency:review-reward:";
    private static final String IDEMPOTENCY_PREFIX_VOTE = "idempotency:vote-reward:";

    /**
     * Consume CommentCreatedEvent from engagement service
     */
    @KafkaListener(topics = "comment-events", groupId = "gamification-service")
    public void handleCommentCreatedEvent(@Payload String eventJson) {
        try {
            // Parse JSON to extract commentId and userId
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(eventJson);
            Integer commentId = jsonNode.get("commentId").asInt();
            String userIdStr = jsonNode.get("userId").asText();
            UUID userId = UUID.fromString(userIdStr);
            
            // Idempotency check: each comment should only be rewarded once (hybrid: Redis + Database)
            String idempotencyKey = IDEMPOTENCY_PREFIX_COMMENT + commentId;
            if (idempotencyService.isProcessed(idempotencyKey, "CommentReward")) {
                log.info("Comment reward already processed, skipping: commentId={}", commentId);
                return;
            }
            
            // Process comment reward
            gamificationService.processUserComment(userId, commentId.longValue());
            
            // Mark as processed (both Redis and Database)
            idempotencyService.markAsProcessed(idempotencyKey, "CommentReward");
            log.info("Successfully processed CommentCreatedEvent: commentId={}, userId={}", commentId, userId);
            
        } catch (Exception e) {
            log.error("Error processing CommentCreatedEvent: {}", eventJson, e);
            // Re-throw to trigger Kafka retry mechanism
            throw new RuntimeException("Failed to process CommentCreatedEvent", e);
        }
    }

    /**
     * Consume ReviewCreatedEvent from engagement service
     */
    @KafkaListener(topics = "review-events", groupId = "gamification-service")
    public void handleReviewCreatedEvent(@Payload String eventJson) {
        try {
            // Parse JSON to extract reviewId and userId
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(eventJson);
            Integer reviewId = jsonNode.get("reviewId").asInt();
            String userIdStr = jsonNode.get("userId").asText();
            UUID userId = UUID.fromString(userIdStr);
            
            // Idempotency check: each review should only be rewarded once (hybrid: Redis + Database)
            String idempotencyKey = IDEMPOTENCY_PREFIX_REVIEW + reviewId;
            if (idempotencyService.isProcessed(idempotencyKey, "ReviewReward")) {
                log.info("Review reward already processed, skipping: reviewId={}", reviewId);
                return;
            }
            
            // Process review reward
            gamificationService.processUserReview(userId, reviewId.longValue());
            
            // Mark as processed (both Redis and Database)
            idempotencyService.markAsProcessed(idempotencyKey, "ReviewReward");
            log.info("Successfully processed ReviewCreatedEvent: reviewId={}, userId={}", reviewId, userId);
            
        } catch (Exception e) {
            log.error("Error processing ReviewCreatedEvent: {}", eventJson, e);
            // Re-throw to trigger Kafka retry mechanism
            throw new RuntimeException("Failed to process ReviewCreatedEvent", e);
        }
    }

    /**
     * Consume VoteCreatedEvent from engagement service
     */
    @KafkaListener(topics = "vote-events", groupId = "gamification-service")
    public void handleVoteCreatedEvent(@Payload String eventJson) {
        try {
            // Parse JSON to extract voteId and userId
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(eventJson);
            Integer voteId = jsonNode.has("voteId") ? jsonNode.get("voteId").asInt() : null;
            String userIdStr = jsonNode.get("userId").asText();
            UUID userId = UUID.fromString(userIdStr);
            
            // Idempotency check: each vote should only be rewarded once (hybrid: Redis + Database)
            // Use voteId if available, otherwise use userId + timestamp
            String idempotencyKey;
            if (voteId != null) {
                idempotencyKey = IDEMPOTENCY_PREFIX_VOTE + voteId;
            } else {
                // Fallback: use userId + current timestamp (rounded to minute)
                long timestampMinutes = System.currentTimeMillis() / (60 * 1000);
                idempotencyKey = IDEMPOTENCY_PREFIX_VOTE + userId + ":" + timestampMinutes;
            }
            
            if (idempotencyService.isProcessed(idempotencyKey, "VoteReward")) {
                log.info("Vote reward already processed, skipping: voteId={}, userId={}", voteId, userId);
                return;
            }
            
            // Process vote reward (EXP only, Yuan deduction is handled separately)
            gamificationService.processUserVote(userId);
            
            // Mark as processed (both Redis and Database)
            idempotencyService.markAsProcessed(idempotencyKey, "VoteReward");
            log.info("Successfully processed VoteCreatedEvent: voteId={}, userId={}", voteId, userId);
            
        } catch (Exception e) {
            log.error("Error processing VoteCreatedEvent: {}", eventJson, e);
            // Re-throw to trigger Kafka retry mechanism
            throw new RuntimeException("Failed to process VoteCreatedEvent", e);
        }
    }
}
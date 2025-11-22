package com.yushan.gamification_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yushan.gamification_service.dto.event.LevelUpEvent;
import com.yushan.gamification_service.service.AchievementService;
import com.yushan.gamification_service.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InternalEventListener {

    private static final Logger log = LoggerFactory.getLogger(InternalEventListener.class);

    @Autowired
    private AchievementService achievementService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdempotencyService idempotencyService;

    private static final String IDEMPOTENCY_PREFIX_LEVEL_UP = "idempotency:level-up:";

    @KafkaListener(topics = "internal_gamification_events", groupId = "gamification-service-internal")
    public void handleLevelUpEvent(String message) {
        try {
            LevelUpEvent event = objectMapper.readValue(message, LevelUpEvent.class);
            UUID userId = event.userId();
            int newLevel = event.newLevel();
            
            // Idempotency check: level-up achievement check should only happen once per user+level (hybrid: Redis + Database)
            // (Note: checkAndUnlockLevelAchievements is naturally idempotent, but this prevents duplicate processing)
            String idempotencyKey = IDEMPOTENCY_PREFIX_LEVEL_UP + userId + ":" + newLevel;
            if (idempotencyService.isProcessed(idempotencyKey, "LevelUpAchievement")) {
                log.info("Level-up achievement check already processed, skipping: userId={}, level={}", userId, newLevel);
                return;
            }
            
            log.info("Received internal LevelUpEvent for user {}, new level: {}", userId, newLevel);
            achievementService.checkAndUnlockLevelAchievements(userId, newLevel);
            
            // Mark as processed (both Redis and Database)
            idempotencyService.markAsProcessed(idempotencyKey, "LevelUpAchievement");
            log.info("Successfully processed LevelUpEvent: userId={}, level={}", userId, newLevel);

        } catch (Exception e) {
            log.error("Failed to process internal LevelUpEvent: {}", message, e);
            // Re-throw to trigger Kafka retry mechanism
            throw new RuntimeException("Failed to process LevelUpEvent", e);
        }
    }
}

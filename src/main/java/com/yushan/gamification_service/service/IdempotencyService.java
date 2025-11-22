package com.yushan.gamification_service.service;

import com.yushan.gamification_service.dao.ProcessedEventMapper;
import com.yushan.gamification_service.entity.ProcessedEvent;
import com.yushan.gamification_service.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Hybrid idempotency service: Redis (fast) + Database (persistent)
 * 
 * Flow:
 * 1. Check Redis → If exists → Skip (fast path)
 * 2. If Redis not found → Check Database (persistent)
 * 3. If Database not found → Process event + Save both Redis + Database
 * 4. If Database found → Skip + Backfill Redis cache
 */
@Slf4j
@Service
public class IdempotencyService {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ProcessedEventMapper processedEventMapper;

    private static final Duration REDIS_TTL = Duration.ofDays(7); // Keep in Redis for 7 days
    private static final String SERVICE_NAME = "gamification-service";

    /**
     * Check if an event has already been processed (hybrid: Redis + Database)
     * 
     * @param idempotencyKey Unique key for the event
     * @param eventType Type of event (e.g., "CommentReward", "UserRegistration")
     * @return true if already processed, false otherwise
     */
    public boolean isProcessed(String idempotencyKey, String eventType) {
        // 1. Check Redis first (fast path)
        if (redisUtil.exists(idempotencyKey)) {
            log.debug("Event already processed (Redis cache): {}", idempotencyKey);
            return true;
        }

        // 2. Check Database (persistent, fallback)
        boolean existsInDb = processedEventMapper.existsByIdempotencyKey(idempotencyKey);
        if (existsInDb) {
            log.debug("Event already processed (Database): {}", idempotencyKey);
            // Backfill Redis cache for faster future checks
            redisUtil.set(idempotencyKey, "processed", REDIS_TTL);
            return true;
        }

        return false;
    }

    /**
     * Mark an event as processed (save to both Redis and Database)
     * 
     * @param idempotencyKey Unique key for the event
     * @param eventType Type of event
     * @param eventData Optional JSON string with event details (for debugging)
     */
    @Transactional
    public void markAsProcessed(String idempotencyKey, String eventType, String eventData) {
        // Save to Redis (fast access)
        redisUtil.set(idempotencyKey, "processed", REDIS_TTL);
        
        // Save to Database (persistent)
        ProcessedEvent event = new ProcessedEvent();
        event.setIdempotencyKey(idempotencyKey);
        event.setEventType(eventType);
        event.setServiceName(SERVICE_NAME);
        event.setProcessedAt(LocalDateTime.now());
        event.setEventData(eventData);
        
        int result = processedEventMapper.insert(event);
        if (result > 0) {
            log.debug("Marked event as processed (both Redis and Database): {}", idempotencyKey);
        } else {
            log.warn("Failed to insert processed event to database (may already exist): {}", idempotencyKey);
        }
    }

    /**
     * Mark an event as processed without event data
     */
    public void markAsProcessed(String idempotencyKey, String eventType) {
        markAsProcessed(idempotencyKey, eventType, null);
    }

    /**
     * Cleanup old processed events (older than specified days)
     * Should be called periodically via scheduled job
     */
    @Transactional
    public void cleanupOldProcessedEvents(int daysToKeep) {
        LocalDateTime beforeDate = LocalDateTime.now().minusDays(daysToKeep);
        int deleted = processedEventMapper.deleteOldProcessedEvents(beforeDate);
        log.info("Cleaned up {} old processed events (older than {} days)", deleted, daysToKeep);
    }
}


package com.yushan.gamification_service.dao;

import com.yushan.gamification_service.entity.ProcessedEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for processed_events table to ensure idempotency
 */
@Mapper
public interface ProcessedEventMapper {

    /**
     * Check if an event with the given idempotency key has been processed
     */
    boolean existsByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * Insert a processed event record
     */
    int insert(ProcessedEvent processedEvent);

    /**
     * Delete old processed events (for cleanup)
     */
    int deleteOldProcessedEvents(@Param("beforeDate") java.time.LocalDateTime beforeDate);
}


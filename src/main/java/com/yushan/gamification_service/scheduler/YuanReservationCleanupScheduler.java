package com.yushan.gamification_service.scheduler;

import com.yushan.gamification_service.service.YuanReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task for cleaning up expired Yuan reservations
 */
@Slf4j
@Component
public class YuanReservationCleanupScheduler {

    @Autowired
    private YuanReservationService yuanReservationService;

    /**
     * Cleanup expired reservations every 5 minutes
     * Runs at fixed rate: every 5 minutes after previous execution completes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    public void cleanupExpiredReservations() {
        try {
            log.debug("Starting cleanup of expired Yuan reservations");
            int releasedCount = yuanReservationService.cleanupExpiredReservations();
            if (releasedCount > 0) {
                log.info("Cleaned up {} expired Yuan reservations", releasedCount);
            } else {
                log.debug("No expired Yuan reservations to clean up");
            }
        } catch (Exception e) {
            log.error("Error during cleanup of expired Yuan reservations", e);
        }
    }
}



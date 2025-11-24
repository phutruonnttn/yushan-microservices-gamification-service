package com.yushan.gamification_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Helper service to publish Kafka events AFTER transaction commit.
 * 
 * This ensures that Kafka events are only published if the database transaction
 * successfully commits, preventing inconsistencies between database state and event consumers.
 */
@Slf4j
@Component
public class TransactionAwareKafkaPublisher {

    /**
     * Schedule a Kafka event to be published AFTER the current transaction commits.
     * If there's no active transaction, the event is published immediately.
     * 
     * @param publishAction The action to publish the Kafka event
     */
    public void publishAfterCommit(Runnable publishAction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Register callback to run AFTER transaction commit
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            publishAction.run();
                        } catch (Exception e) {
                            log.error("Failed to publish Kafka event after transaction commit", e);
                            // Don't throw exception - transaction already committed
                        }
                    }
                }
            );
        } else {
            // No active transaction - publish immediately
            try {
                publishAction.run();
            } catch (Exception e) {
                log.error("Failed to publish Kafka event (no transaction)", e);
            }
        }
    }
}


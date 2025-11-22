package com.yushan.gamification_service.entity;

import java.time.LocalDateTime;

/**
 * Entity for tracking processed events to ensure idempotency
 */
public class ProcessedEvent {
    
    private String idempotencyKey;
    private String eventType;
    private String serviceName;
    private LocalDateTime processedAt;
    private String eventData; // JSON string (optional)

    public ProcessedEvent() {
    }

    public ProcessedEvent(String idempotencyKey, String eventType, String serviceName, LocalDateTime processedAt, String eventData) {
        this.idempotencyKey = idempotencyKey;
        this.eventType = eventType;
        this.serviceName = serviceName;
        this.processedAt = processedAt;
        this.eventData = eventData;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getEventData() {
        return eventData;
    }

    public void setEventData(String eventData) {
        this.eventData = eventData;
    }
}


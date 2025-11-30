package com.yushan.gamification_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vote SAGA Start Event
 * Published by Engagement Service when starting vote creation SAGA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteSagaStartEvent {
    
    /**
     * Unique SAGA ID for tracking this distributed transaction
     */
    @JsonProperty("sagaId")
    private String sagaId;
    
    /**
     * User ID who wants to vote
     */
    @JsonProperty("userId")
    private UUID userId;
    
    /**
     * Novel ID to vote for
     */
    @JsonProperty("novelId")
    private Integer novelId;
    
    /**
     * Timestamp when SAGA started
     */
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
}



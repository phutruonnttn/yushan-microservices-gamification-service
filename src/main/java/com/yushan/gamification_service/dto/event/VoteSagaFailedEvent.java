package com.yushan.gamification_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vote SAGA Failed Event
 * Published when SAGA fails at any step
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteSagaFailedEvent {
    
    /**
     * Unique SAGA ID
     */
    @JsonProperty("sagaId")
    private String sagaId;
    
    /**
     * User ID
     */
    @JsonProperty("userId")
    private UUID userId;
    
    /**
     * Novel ID
     */
    @JsonProperty("novelId")
    private Integer novelId;
    
    /**
     * Failure reason
     */
    @JsonProperty("reason")
    private String reason;
    
    /**
     * Reservation ID (if any)
     */
    @JsonProperty("reservationId")
    private UUID reservationId;
    
    /**
     * Timestamp when failure occurred
     */
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
}



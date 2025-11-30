package com.yushan.gamification_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vote SAGA Compensate Yuan Event
 * Published when compensation is needed (release Yuan reservation)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteSagaCompensateYuanEvent {
    
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
     * Reservation ID to release
     */
    @JsonProperty("reservationId")
    private UUID reservationId;
    
    /**
     * Compensation reason
     */
    @JsonProperty("reason")
    private String reason;
    
    /**
     * Timestamp when compensation triggered
     */
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
}



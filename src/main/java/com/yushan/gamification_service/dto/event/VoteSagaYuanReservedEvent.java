package com.yushan.gamification_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vote SAGA Yuan Reserved Event
 * Published by Gamification Service after successfully reserving Yuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteSagaYuanReservedEvent {
    
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
     * Reservation ID for tracking
     */
    @JsonProperty("reservationId")
    private UUID reservationId;
    
    /**
     * Timestamp when Yuan was reserved
     */
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
}



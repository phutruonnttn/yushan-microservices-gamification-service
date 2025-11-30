package com.yushan.gamification_service.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vote SAGA Vote Created Event
 * Published by Engagement Service after successfully creating vote
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteSagaVoteCreatedEvent {
    
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
     * Vote ID (created vote)
     */
    @JsonProperty("voteId")
    private Integer voteId;
    
    /**
     * Reservation ID
     */
    @JsonProperty("reservationId")
    private UUID reservationId;
    
    /**
     * Timestamp when vote was created
     */
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
}



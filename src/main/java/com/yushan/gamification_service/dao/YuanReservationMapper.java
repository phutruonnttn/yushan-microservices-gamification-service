package com.yushan.gamification_service.dao;

import com.yushan.gamification_service.entity.YuanReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface YuanReservationMapper {
    
    /**
     * Insert a new Yuan reservation
     */
    int insert(YuanReservation reservation);
    
    /**
     * Find reservation by reservation ID
     */
    YuanReservation findByReservationId(@Param("reservationId") UUID reservationId);
    
    /**
     * Find reservation by saga ID
     */
    YuanReservation findBySagaId(@Param("sagaId") String sagaId);
    
    /**
     * Update reservation status to CONFIRMED
     */
    int confirmReservation(@Param("reservationId") UUID reservationId, @Param("confirmedAt") OffsetDateTime confirmedAt);
    
    /**
     * Update reservation status to RELEASED
     */
    int releaseReservation(@Param("reservationId") UUID reservationId, @Param("releasedAt") OffsetDateTime releasedAt);
    
    /**
     * Find all expired reservations with status RESERVED
     * Used for cleanup job
     */
    List<YuanReservation> findExpiredReservations(@Param("currentTime") OffsetDateTime currentTime);
    
    /**
     * Count reservations by user ID and status
     * Used to check if user has pending reservations
     */
    int countByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") String status);
    
    /**
     * Sum reserved amounts for a user (status = RESERVED)
     * Used to calculate available balance when reserving
     */
    Double sumReservedAmountByUserId(@Param("userId") UUID userId);
}



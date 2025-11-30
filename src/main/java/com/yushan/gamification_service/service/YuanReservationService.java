package com.yushan.gamification_service.service;

import com.yushan.gamification_service.dao.YuanReservationMapper;
import com.yushan.gamification_service.entity.YuanReservation;
import com.yushan.gamification_service.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing Yuan reservations in SAGA pattern
 * Handles reserve, confirm, and release operations
 */
@Slf4j
@Service
public class YuanReservationService {

    @Autowired
    private YuanReservationMapper yuanReservationMapper;
    
    @Autowired
    private com.yushan.gamification_service.repository.UserProgressRepository userProgressRepository;

    @Value("${saga.yuan-reservation.timeout-minutes:5}")
    private int reservationTimeoutMinutes;

    @Value("${saga.yuan-reservation.enabled:true}")
    private boolean sagaEnabled;

    /**
     * Reserve Yuan for a SAGA transaction
     * Creates a pending reservation that will expire after timeout
     * 
     * @param userId User ID
     * @param amount Amount to reserve (must be positive)
     * @param sagaId Unique SAGA ID for tracking
     * @return Reservation ID
     */
    @Transactional
    public UUID reserveYuan(UUID userId, Double amount, String sagaId) {
        log.info("Reserving {} Yuan for user {} in SAGA {}", amount, userId, sagaId);

        // Validate amount
        if (amount == null || amount <= 0) {
            throw new ValidationException("Amount must be greater than 0");
        }

        // Check if user has sufficient balance (check BEFORE reserving)
        Double currentBalance = userProgressRepository.sumYuanAmountByUserId(userId);
        double totalBalance = (currentBalance == null) ? 0.0 : currentBalance;
        
        // Calculate reserved amount (pending reservations)
        Double reservedAmount = yuanReservationMapper.sumReservedAmountByUserId(userId);
        double totalReserved = (reservedAmount == null) ? 0.0 : reservedAmount;
        
        // Available balance = total balance - already reserved
        double availableBalance = totalBalance - totalReserved;
        
        if (availableBalance < amount) {
            log.error("Insufficient balance to reserve Yuan: user {} has {} available (total: {}, reserved: {}), needs {}", 
                    userId, availableBalance, totalBalance, totalReserved, amount);
            throw new ValidationException("Insufficient Yuan balance. Available: " + availableBalance + ", Required: " + amount);
        }
        
        // Create reservation
        UUID reservationId = UUID.randomUUID();
        YuanReservation reservation = new YuanReservation();
        reservation.setReservationId(reservationId);
        reservation.setUserId(userId);
        reservation.setAmount(amount);
        reservation.setSagaId(sagaId);
        reservation.setStatus(YuanReservation.ReservationStatus.RESERVED);
        reservation.setExpiresAt(OffsetDateTime.now().plus(reservationTimeoutMinutes, ChronoUnit.MINUTES));
        
        yuanReservationMapper.insert(reservation);
        
        log.info("Successfully reserved {} Yuan for user {} in SAGA {}, reservationId: {}", 
                amount, userId, sagaId, reservationId);
        
        return reservationId;
    }

    /**
     * Confirm a Yuan reservation (convert to actual deduction)
     * Checks balance, creates YuanTransaction and marks reservation as CONFIRMED
     * 
     * @param reservationId Reservation ID
     * @param userId User ID (for validation)
     * @param userProgressRepository Repository for balance check and transaction creation
     * @return true if confirmed successfully
     */
    @Transactional
    public boolean confirmReservation(UUID reservationId, UUID userId, com.yushan.gamification_service.repository.UserProgressRepository userProgressRepository) {
        log.info("Confirming Yuan reservation {} for user {}", reservationId, userId);

        YuanReservation reservation = yuanReservationMapper.findByReservationId(reservationId);
        if (reservation == null) {
            log.error("Reservation not found: {}", reservationId);
            throw new ValidationException("Reservation not found: " + reservationId);
        }

        // Validate user matches
        if (!reservation.getUserId().equals(userId)) {
            log.error("Reservation user mismatch: reservation userId={}, provided userId={}", 
                    reservation.getUserId(), userId);
            throw new ValidationException("Reservation does not belong to user");
        }

        // Validate status
        if (reservation.getStatus() != YuanReservation.ReservationStatus.RESERVED) {
            log.error("Reservation is not in RESERVED status: {}", reservation.getStatus());
            throw new ValidationException("Reservation is not in RESERVED status");
        }

        // Check if expired
        if (reservation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            log.error("Reservation has expired: {}", reservationId);
            // Auto-release expired reservation
            releaseReservation(reservationId, userId);
            throw new ValidationException("Reservation has expired");
        }

        // Check if user has sufficient balance (including this reservation)
        Double currentBalance = userProgressRepository.sumYuanAmountByUserId(userId);
        double availableBalance = (currentBalance == null) ? 0.0 : currentBalance;
        
        if (availableBalance < reservation.getAmount()) {
            log.error("Insufficient balance: user {} has {} but needs {}", userId, availableBalance, reservation.getAmount());
            // Release reservation and throw exception
            releaseReservation(reservationId, userId);
            throw new ValidationException("Insufficient Yuan balance");
        }

        // Create Yuan transaction (deduct)
        com.yushan.gamification_service.entity.YuanTransaction yuanTransaction = 
            new com.yushan.gamification_service.entity.YuanTransaction();
        yuanTransaction.setUserId(userId);
        yuanTransaction.setAmount(-reservation.getAmount());
        yuanTransaction.setDescription("Vote cost (SAGA confirmed)");
        userProgressRepository.saveYuanTransaction(yuanTransaction);

        // Update reservation status to CONFIRMED
        int updated = yuanReservationMapper.confirmReservation(reservationId, OffsetDateTime.now());
        if (updated == 0) {
            log.error("Failed to confirm reservation: {}", reservationId);
            throw new ValidationException("Failed to confirm reservation");
        }

        log.info("Successfully confirmed Yuan reservation {} for user {}, deducted {} Yuan", 
                reservationId, userId, reservation.getAmount());
        return true;
    }

    /**
     * Release a Yuan reservation (compensation/rollback)
     * Marks reservation as RELEASED without creating transaction
     * 
     * @param reservationId Reservation ID
     * @param userId User ID (for validation)
     * @return true if released successfully
     */
    @Transactional
    public boolean releaseReservation(UUID reservationId, UUID userId) {
        log.info("Releasing Yuan reservation {} for user {}", reservationId, userId);

        YuanReservation reservation = yuanReservationMapper.findByReservationId(reservationId);
        if (reservation == null) {
            log.warn("Reservation not found for release: {}", reservationId);
            return false; // Already released or doesn't exist
        }

        // Validate user matches
        if (!reservation.getUserId().equals(userId)) {
            log.error("Reservation user mismatch: reservation userId={}, provided userId={}", 
                    reservation.getUserId(), userId);
            throw new ValidationException("Reservation does not belong to user");
        }

        // Can only release RESERVED or CONFIRMED reservations
        if (reservation.getStatus() == YuanReservation.ReservationStatus.RELEASED) {
            log.warn("Reservation already released: {}", reservationId);
            return true; // Already released
        }

        // Update reservation status to RELEASED
        int updated = yuanReservationMapper.releaseReservation(reservationId, OffsetDateTime.now());
        if (updated == 0) {
            log.warn("Failed to release reservation (may already be processed): {}", reservationId);
            return false;
        }

        log.info("Successfully released Yuan reservation {} for user {}", reservationId, userId);
        return true;
    }

    /**
     * Release reservation by SAGA ID (for compensation)
     * 
     * @param sagaId SAGA ID
     * @return true if released successfully
     */
    @Transactional
    public boolean releaseReservationBySagaId(String sagaId) {
        log.info("Releasing Yuan reservation by SAGA ID: {}", sagaId);

        YuanReservation reservation = yuanReservationMapper.findBySagaId(sagaId);
        if (reservation == null) {
            log.warn("Reservation not found for SAGA ID: {}", sagaId);
            return false;
        }

        return releaseReservation(reservation.getReservationId(), reservation.getUserId());
    }

    /**
     * Get reservation by SAGA ID
     * 
     * @param sagaId SAGA ID
     * @return Reservation or null if not found
     */
    public YuanReservation getReservationBySagaId(String sagaId) {
        return yuanReservationMapper.findBySagaId(sagaId);
    }

    /**
     * Get reservation by reservation ID
     * 
     * @param reservationId Reservation ID
     * @return Reservation or null if not found
     */
    public YuanReservation getReservation(UUID reservationId) {
        return yuanReservationMapper.findByReservationId(reservationId);
    }

    /**
     * Find all expired reservations (for cleanup job)
     * 
     * @return List of expired reservations
     */
    public List<YuanReservation> findExpiredReservations() {
        return yuanReservationMapper.findExpiredReservations(OffsetDateTime.now());
    }

    /**
     * Cleanup expired reservations (should be called by scheduled job)
     * Releases all expired RESERVED reservations
     */
    @Transactional
    public int cleanupExpiredReservations() {
        log.info("Starting cleanup of expired Yuan reservations");
        
        List<YuanReservation> expiredReservations = findExpiredReservations();
        int releasedCount = 0;

        for (YuanReservation reservation : expiredReservations) {
            try {
                if (releaseReservation(reservation.getReservationId(), reservation.getUserId())) {
                    releasedCount++;
                    log.debug("Released expired reservation: {}", reservation.getReservationId());
                }
            } catch (Exception e) {
                log.error("Failed to release expired reservation: {}", reservation.getReservationId(), e);
            }
        }

        log.info("Cleaned up {} expired Yuan reservations", releasedCount);
        return releasedCount;
    }
}


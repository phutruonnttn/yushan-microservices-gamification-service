package com.yushan.gamification_service.repository;

import com.yushan.gamification_service.dto.achievement.AchievementDTO;
import com.yushan.gamification_service.entity.Achievement;
import com.yushan.gamification_service.entity.DailyRewardLog;
import com.yushan.gamification_service.entity.ExpTransaction;
import com.yushan.gamification_service.entity.UserAchievement;
import com.yushan.gamification_service.entity.YuanTransaction;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for UserProgress aggregate.
 * Abstracts data access operations for ExpTransaction, YuanTransaction, Achievement, UserAchievement, DailyRewardLog.
 */
public interface UserProgressRepository {
    
    // ExpTransaction operations
    void saveExpTransaction(ExpTransaction transaction);
    
    Double sumExpAmountByUserId(UUID userId);
    
    List<Map<String, Object>> sumExpAmountGroupedByUser();
    
    List<Map<String, Object>> sumExpAmountGroupedByUsers(List<UUID> userIds);
    
    // YuanTransaction operations
    void saveYuanTransaction(YuanTransaction transaction);
    
    Double sumYuanAmountByUserId(UUID userId);
    
    List<YuanTransaction> findYuanTransactionsByUserIdPaged(UUID userId, int offset, int size);
    
    List<YuanTransaction> findYuanTransactionsWithFilters(
            UUID userId,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            int offset,
            int size
    );
    
    long countYuanTransactionsWithFilters(
            UUID userId,
            OffsetDateTime startDate,
            OffsetDateTime endDate
    );
    
    long countYuanTransactionsByUserId(UUID userId);
    
    // Achievement operations
    Optional<Achievement> findAchievementById(String id);
    
    List<Achievement> findAllAchievements();
    
    void saveAchievement(Achievement achievement);
    
    // UserAchievement operations
    void saveUserAchievement(UserAchievement userAchievement);
    
    List<UserAchievement> findUserAchievementsByUserId(UUID userId);
    
    Long findUserAchievementByUserIdAndAchievementId(UUID userId, String achievementId);
    
    List<AchievementDTO> findUnlockedAchievementsByUserId(UUID userId);
    
    // DailyRewardLog operations
    Optional<DailyRewardLog> findDailyRewardLogByUserId(UUID userId);
    
    void saveDailyRewardLog(DailyRewardLog log);
    
    void updateDailyRewardLog(DailyRewardLog log);
}


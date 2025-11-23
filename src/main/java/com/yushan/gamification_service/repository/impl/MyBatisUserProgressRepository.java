package com.yushan.gamification_service.repository.impl;

import com.yushan.gamification_service.dao.*;
import com.yushan.gamification_service.dto.achievement.AchievementDTO;
import com.yushan.gamification_service.entity.Achievement;
import com.yushan.gamification_service.entity.DailyRewardLog;
import com.yushan.gamification_service.entity.ExpTransaction;
import com.yushan.gamification_service.entity.UserAchievement;
import com.yushan.gamification_service.entity.YuanTransaction;
import com.yushan.gamification_service.repository.UserProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MyBatis implementation of UserProgressRepository.
 * Handles aggregate-level operations for UserProgress (ExpTransaction, YuanTransaction, Achievement, UserAchievement, DailyRewardLog).
 */
@Repository
public class MyBatisUserProgressRepository implements UserProgressRepository {
    
    @Autowired
    private ExpTransactionMapper expTransactionMapper;
    
    @Autowired
    private YuanTransactionMapper yuanTransactionMapper;
    
    @Autowired
    private AchievementMapper achievementMapper;
    
    @Autowired
    private UserAchievementMapper userAchievementMapper;
    
    @Autowired
    private DailyRewardLogMapper dailyRewardLogMapper;
    
    // ExpTransaction operations
    @Override
    public void saveExpTransaction(ExpTransaction transaction) {
        expTransactionMapper.insert(transaction);
    }
    
    @Override
    public Double sumExpAmountByUserId(UUID userId) {
        return expTransactionMapper.sumAmountByUserId(userId);
    }
    
    @Override
    public List<Map<String, Object>> sumExpAmountGroupedByUser() {
        return expTransactionMapper.sumAmountGroupedByUser();
    }
    
    @Override
    public List<Map<String, Object>> sumExpAmountGroupedByUsers(List<UUID> userIds) {
        return expTransactionMapper.sumAmountGroupedByUsers(userIds);
    }
    
    // YuanTransaction operations
    @Override
    public void saveYuanTransaction(YuanTransaction transaction) {
        yuanTransactionMapper.insert(transaction);
    }
    
    @Override
    public Double sumYuanAmountByUserId(UUID userId) {
        return yuanTransactionMapper.sumAmountByUserId(userId);
    }
    
    @Override
    public List<YuanTransaction> findYuanTransactionsByUserIdPaged(UUID userId, int offset, int size) {
        return yuanTransactionMapper.findByUserIdPaged(userId, offset, size);
    }
    
    @Override
    public List<YuanTransaction> findYuanTransactionsWithFilters(
            UUID userId,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            int offset,
            int size
    ) {
        return yuanTransactionMapper.findWithFilters(userId, startDate, endDate, offset, size);
    }
    
    @Override
    public long countYuanTransactionsWithFilters(
            UUID userId,
            OffsetDateTime startDate,
            OffsetDateTime endDate
    ) {
        return yuanTransactionMapper.countWithFilters(userId, startDate, endDate);
    }
    
    @Override
    public long countYuanTransactionsByUserId(UUID userId) {
        return yuanTransactionMapper.countByUserId(userId);
    }
    
    // Achievement operations
    @Override
    public Optional<Achievement> findAchievementById(String id) {
        return achievementMapper.findById(id);
    }
    
    @Override
    public List<Achievement> findAllAchievements() {
        return achievementMapper.findAll();
    }
    
    @Override
    public void saveAchievement(Achievement achievement) {
        achievementMapper.insert(achievement);
    }
    
    // UserAchievement operations
    @Override
    public void saveUserAchievement(UserAchievement userAchievement) {
        userAchievementMapper.insert(userAchievement);
    }
    
    @Override
    public List<UserAchievement> findUserAchievementsByUserId(UUID userId) {
        return userAchievementMapper.findByUserId(userId);
    }
    
    @Override
    public Long findUserAchievementByUserIdAndAchievementId(UUID userId, String achievementId) {
        return userAchievementMapper.findByUserIdAndAchievementId(userId, achievementId);
    }
    
    @Override
    public List<AchievementDTO> findUnlockedAchievementsByUserId(UUID userId) {
        return userAchievementMapper.findUnlockedAchievementsByUserId(userId);
    }
    
    // DailyRewardLog operations
    @Override
    public Optional<DailyRewardLog> findDailyRewardLogByUserId(UUID userId) {
        return dailyRewardLogMapper.findByUserId(userId);
    }
    
    @Override
    public void saveDailyRewardLog(DailyRewardLog log) {
        dailyRewardLogMapper.insert(log);
    }
    
    @Override
    public void updateDailyRewardLog(DailyRewardLog log) {
        dailyRewardLogMapper.update(log);
    }
}


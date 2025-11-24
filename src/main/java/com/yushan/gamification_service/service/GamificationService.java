package com.yushan.gamification_service.service;

import com.yushan.gamification_service.repository.UserProgressRepository;
import com.yushan.gamification_service.dto.achievement.AchievementDTO;
import com.yushan.gamification_service.dto.stats.GamificationStatsDTO;
import com.yushan.gamification_service.dto.user.UserLevelDTO;
import com.yushan.gamification_service.dto.vote.VoteCheckResponseDTO;
import com.yushan.gamification_service.dto.transaction.YuanTransactionDTO;
import com.yushan.gamification_service.entity.DailyRewardLog;
import com.yushan.gamification_service.entity.ExpTransaction;
import com.yushan.gamification_service.entity.YuanTransaction;
import com.yushan.gamification_service.dto.event.LevelUpEvent;
import com.yushan.gamification_service.exception.ValidationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.yushan.gamification_service.dto.common.PageResponseDTO;
import com.yushan.gamification_service.dto.transaction.AdminYuanTransactionDTO;
import java.time.OffsetDateTime;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GamificationService {

    private static final Logger logger = LoggerFactory.getLogger(GamificationService.class);

    @Value("${gamification.rewards.daily-login-exp:5}")
    private double dailyLoginExp;

    @Value("${gamification.rewards.comment-exp:5}")
    private double commentExp;

    @Value("${gamification.rewards.review-exp:5}")
    private double reviewExp;

    @Value("2")
    private double registrationYuan;

    @Value("${gamification.rewards.vote-exp:3}")
    private double voteExp;

    @Autowired
    private UserProgressRepository userProgressRepository;
    
    @Autowired
    private LevelService levelService;
    
    @Autowired
    private AchievementService achievementService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private TransactionAwareKafkaPublisher transactionAwareKafkaPublisher;

    private static final String INTERNAL_EVENTS_TOPIC = "internal_gamification_events";

    @Transactional
    public void processUserRegistration(UUID userId) {
        YuanTransaction yuanTransaction = new YuanTransaction();
        yuanTransaction.setUserId(userId);
        yuanTransaction.setAmount(registrationYuan);
        yuanTransaction.setDescription("Registration Reward");
        userProgressRepository.saveYuanTransaction(yuanTransaction);
        logger.debug("Inserted Yuan transaction for user: {} with amount: {}", userId, registrationYuan);
    }

    @Transactional
    public void processUserLogin(UUID userId) {
        logger.info("Processing login for user: {}", userId);

        LocalDate today = LocalDate.now();
        Optional<DailyRewardLog> rewardLogOpt = userProgressRepository.findDailyRewardLogByUserId(userId);

        if (rewardLogOpt.isPresent() && rewardLogOpt.get().getLastRewardDate().isEqual(today)) {
            logger.info("User {} has already claimed the daily reward today.", userId);
            achievementService.checkAndUnlockLoginAchievements(userId);
            return;
        }

        logger.info("Awarding daily login reward to user: {}", userId);
        
        // Calculate current level to determine Yuan reward
        Double currentTotalExp = userProgressRepository.sumExpAmountByUserId(userId);
        double currentTotalExpValue = (currentTotalExp == null) ? 0.0 : currentTotalExp;
        int currentLevel = levelService.calculateLevel(currentTotalExpValue);
        
        // (EXP)
        ExpTransaction expTransaction = new ExpTransaction();
        expTransaction.setUserId(userId);
        expTransaction.setAmount(dailyLoginExp);
        expTransaction.setReason("Daily Login Reward");
        userProgressRepository.saveExpTransaction(expTransaction);
        logger.debug("Inserted EXP transaction for user: {}", userId);

        // (Yuan) - Yuan = level (matching yushan-backend logic)
        YuanTransaction yuanTransaction = new YuanTransaction();
        yuanTransaction.setUserId(userId);
        yuanTransaction.setAmount((double) currentLevel); // Yuan = level
        yuanTransaction.setDescription("Daily Login Reward");
        userProgressRepository.saveYuanTransaction(yuanTransaction);
        logger.debug("Inserted Yuan transaction for user: {} with amount: {}", userId, currentLevel);

        if (rewardLogOpt.isPresent()) {
            DailyRewardLog rewardLog = rewardLogOpt.get();
            rewardLog.setLastRewardDate(today);
            userProgressRepository.updateDailyRewardLog(rewardLog);
            logger.debug("Updated daily reward log for user: {}", userId);
        } else {
            DailyRewardLog newRewardLog = new DailyRewardLog();
            newRewardLog.setUserId(userId);
            newRewardLog.setLastRewardDate(today);
            userProgressRepository.saveDailyRewardLog(newRewardLog);
            logger.debug("Created new daily reward log for user: {}", userId);
        }

        logger.info("Successfully processed login and awarded daily reward for user: {}", userId);

        checkLevelUpAndPublishEvent(userId, dailyLoginExp);
        achievementService.checkAndUnlockLoginAchievements(userId);
    }

    @Transactional
    public void processUserComment(UUID userId, Long commentId) {
        logger.info("Processing comment event for user: {}, commentId: {}", userId, commentId);

        ExpTransaction expTransaction = new ExpTransaction();
        expTransaction.setUserId(userId);
        expTransaction.setAmount(commentExp);
        expTransaction.setReason("Posted a comment with ID: " + commentId);
        userProgressRepository.saveExpTransaction(expTransaction);
        logger.debug("Awarded {} EXP to user {} for comment {}", commentExp, userId, commentId);

        achievementService.checkAndUnlockCommentAchievements(userId, 1L);

        checkLevelUpAndPublishEvent(userId, commentExp);
    }

    @Transactional
    public void processUserReview(UUID userId, Long reviewId) {
        logger.info("Processing review event for user: {}, reviewId: {}", userId, reviewId);

        ExpTransaction expTransaction = new ExpTransaction();
        expTransaction.setUserId(userId);
        expTransaction.setAmount(reviewExp);
        expTransaction.setReason("Posted a review with ID: " + reviewId);
        userProgressRepository.saveExpTransaction(expTransaction);
        logger.debug("Awarded {} EXP to user {} for review {}", reviewExp, userId, reviewId);

        achievementService.checkAndUnlockReviewAchievements(userId, 1L);

        checkLevelUpAndPublishEvent(userId, reviewExp);
    }


    public GamificationStatsDTO getGamificationStatsForUser(UUID userId) {
        Double totalExp = userProgressRepository.sumExpAmountByUserId(userId);
        Double yuanBalance = userProgressRepository.sumYuanAmountByUserId(userId);

        double totalExpValue = (totalExp == null) ? 0.0 : totalExp;
        double yuanBalanceValue = (yuanBalance == null) ? 0.0 : yuanBalance;

        int currentLevel = levelService.calculateLevel(totalExpValue);

        Double expForNextLevel = levelService.getExpForNextLevel(currentLevel);

        return new GamificationStatsDTO(userId.toString(), currentLevel, totalExpValue, expForNextLevel, yuanBalanceValue);
    }

    public List<YuanTransactionDTO> getTransactionHistory(UUID userId, int page, int size) {
        int offset = page * size;

        List<YuanTransaction> transactions = userProgressRepository.findYuanTransactionsByUserIdPaged(userId, offset, size);

        return transactions.stream()
                .map(t -> {
                    YuanTransactionDTO dto = new YuanTransactionDTO();
                    dto.setAmount(t.getAmount());
                    dto.setDescription(t.getDescription());
                    dto.setCreatedAt(t.getCreatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<AchievementDTO> getUnlockedAchievements(UUID userId) {
        return userProgressRepository.findUnlockedAchievementsByUserId(userId);
    }

    @Transactional
    public void processUserVote(UUID userId) {
        logger.info("Processing vote event for user: {}", userId);

        // Award EXP (matching yushan-backend logic)
        ExpTransaction expTransaction = new ExpTransaction();
        expTransaction.setUserId(userId);
        expTransaction.setAmount(voteExp);
        expTransaction.setReason("Voted on a novel");
        userProgressRepository.saveExpTransaction(expTransaction);

        // Deduct Yuan
        YuanTransaction yuanTransaction = new YuanTransaction();
        yuanTransaction.setUserId(userId);
        yuanTransaction.setAmount(-1.0);
        yuanTransaction.setDescription("Voted on a novel");
        userProgressRepository.saveYuanTransaction(yuanTransaction);

        logger.info("Awarded {} EXP and deducted 1 Yuan from user {} for voting.", voteExp, userId);
        
        checkLevelUpAndPublishEvent(userId, voteExp);
    }

    private void checkLevelUpAndPublishEvent(UUID userId, Double expGained) {
        Double currentTotalExp = userProgressRepository.sumExpAmountByUserId(userId);
        if (currentTotalExp == null) currentTotalExp = 0.0;

        int currentLevel = levelService.calculateLevel(currentTotalExp);

        Double previousTotalExp = currentTotalExp - expGained;
        int previousLevel = levelService.calculateLevel(previousTotalExp);

        if (currentLevel > previousLevel) {
            logger.info("User {} leveled up from {} to {}!", userId, previousLevel, currentLevel);
            // Publish LevelUpEvent AFTER transaction commit
            final UUID finalUserId = userId;
            final int finalCurrentLevel = currentLevel;
            transactionAwareKafkaPublisher.publishAfterCommit(() -> {
                LevelUpEvent event = new LevelUpEvent(finalUserId, finalCurrentLevel);
                kafkaTemplate.send(INTERNAL_EVENTS_TOPIC, event);
                logger.info("Published LevelUpEvent to topic '{}'", INTERNAL_EVENTS_TOPIC);
            });
        }
    }

    public PageResponseDTO<AdminYuanTransactionDTO> findYuanTransactionsForAdmin(
            UUID userId,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            int page,
            int size
    ) {
        int offset = page * size;

        List<YuanTransaction> transactions = userProgressRepository.findYuanTransactionsWithFilters(userId, startDate, endDate, offset, size);

        List<AdminYuanTransactionDTO> dtos = transactions.stream().map(transaction -> {
            AdminYuanTransactionDTO dto = new AdminYuanTransactionDTO();
            dto.setId(transaction.getId());
            dto.setUserId(transaction.getUserId());
            dto.setAmount(transaction.getAmount());
            dto.setDescription(transaction.getDescription());
            dto.setCreatedAt(transaction.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());

        long totalElements = userProgressRepository.countYuanTransactionsWithFilters(userId, startDate, endDate);

        return new PageResponseDTO<>(dtos, totalElements, page, size);
    }

    public UserLevelDTO getUserLevel(UUID userId) {
        Double totalExp = userProgressRepository.sumExpAmountByUserId(userId);
        double totalExpValue = (totalExp == null) ? 0.0 : totalExp;
        
        int currentLevel = levelService.calculateLevel(totalExpValue);
        Double expForNextLevel = levelService.getExpForNextLevel(currentLevel);
        
        // Calculate progress percentage
        double expProgress = 0.0;
        if (expForNextLevel != null && expForNextLevel > 0) {
            // Calculate exp needed for current level
            double expForCurrentLevel = 0.0;
            if (currentLevel > 1) {
                // Get the threshold for previous level
                double[] thresholds = {100, 500, 2000, 5000};
                if (currentLevel - 2 >= 0 && currentLevel - 2 < thresholds.length) {
                    expForCurrentLevel = thresholds[currentLevel - 2];
                }
            }
            expProgress = ((totalExpValue - expForCurrentLevel) / (expForNextLevel.doubleValue() - expForCurrentLevel)) * 100;
        }
        
        return new UserLevelDTO(currentLevel, totalExpValue, expForNextLevel != null ? expForNextLevel.doubleValue() : 0.0, expProgress, null);
    }

    @Transactional
    public void rewardComment(UUID userId, Long commentId) {
        logger.info("Rewarding comment for user: {}, commentId: {}", userId, commentId);
        
        // Award EXP only (matching yushan-backend logic)
        ExpTransaction expTransaction = new ExpTransaction();
        expTransaction.setUserId(userId);
        expTransaction.setAmount(commentExp);
        expTransaction.setReason("Comment reward for comment ID: " + commentId);
        userProgressRepository.saveExpTransaction(expTransaction);
        
        logger.info("Awarded {} EXP to user {} for comment {}", commentExp, userId, commentId);
        
        checkLevelUpAndPublishEvent(userId, commentExp);
    }


    @Transactional
    public void rewardReview(UUID userId, Long reviewId) {
        logger.info("Rewarding review for user: {}, reviewId: {}", userId, reviewId);
        
        // Award EXP only (matching yushan-backend logic)
        ExpTransaction expTransaction = new ExpTransaction();
        expTransaction.setUserId(userId);
        expTransaction.setAmount(reviewExp);
        expTransaction.setReason("Review reward for review ID: " + reviewId);
        userProgressRepository.saveExpTransaction(expTransaction);
        
        logger.info("Awarded {} EXP to user {} for review {}", reviewExp, userId, reviewId);
        
        checkLevelUpAndPublishEvent(userId, reviewExp);
    }


    @Transactional
    public void rewardVote(UUID userId) {
        logger.info("Processing vote reward for user: {}", userId);
        
        // Award EXP only (matching yushan-backend logic)
        ExpTransaction expTransaction = new ExpTransaction();
        expTransaction.setUserId(userId);
        expTransaction.setAmount(voteExp);
        expTransaction.setReason("Vote reward");
        userProgressRepository.saveExpTransaction(expTransaction);
        
        logger.info("Awarded {} EXP to user {} for voting", voteExp, userId);
        
        checkLevelUpAndPublishEvent(userId, voteExp);
    }


    public VoteCheckResponseDTO checkVoteEligibility(UUID userId) {
        Double yuanBalance = userProgressRepository.sumYuanAmountByUserId(userId);
        double currentYuanBalance = (yuanBalance == null) ? 0.0 : yuanBalance;
        double requiredYuan = 1.0;
        
        boolean canVote = currentYuanBalance >= requiredYuan;
        String message = canVote ? 
            "You can vote! You have " + currentYuanBalance + " Yuan." :
            "Insufficient Yuan balance. You need at least " + requiredYuan + " Yuan to vote.";
        
        return new VoteCheckResponseDTO(canVote, currentYuanBalance, requiredYuan, message);
    }

    @Transactional
    public void updateYuanAfterVote(UUID userId) {
        logger.info("Updating Yuan balance after vote for user: {}", userId);
        
        // Deduct 1 Yuan for voting
        YuanTransaction yuanTransaction = new YuanTransaction();
        yuanTransaction.setUserId(userId);
        yuanTransaction.setAmount(-1.0);
        yuanTransaction.setDescription("Vote cost");
        userProgressRepository.saveYuanTransaction(yuanTransaction);
        
        logger.info("Deducted 1 Yuan from user {} for voting", userId);
    }

    /**
     * Admin method to add Yuan to user's balance
     * Only inserts a transaction record - user validation is done via UserServiceClient
     */
    @Transactional
    public void adminAddYuan(UUID userId, Double amount, String reason) {
        logger.info("Admin adding {} Yuan to user: {} with reason: {}", amount, userId, reason);
        
        // Validate amount
        if (amount <= 0) {
            throw new ValidationException("Amount must be greater than 0");
        }
        
        // Create Yuan transaction (positive amount = adding Yuan)
        YuanTransaction yuanTransaction = new YuanTransaction();
        yuanTransaction.setUserId(userId);
        yuanTransaction.setAmount(amount);
        yuanTransaction.setDescription(reason != null ? reason : "Admin adjustment");
        userProgressRepository.saveYuanTransaction(yuanTransaction);
        
        logger.info("Successfully added {} Yuan to user {} by admin", amount, userId);
    }

    public List<GamificationStatsDTO> getAllUsersGamificationStats() {
        List<Map<String, Object>> allUsersExp = userProgressRepository.sumExpAmountGroupedByUser();

        Map<UUID, Double> expMap = allUsersExp.stream()
                .collect(Collectors.toMap(
                        map -> (UUID) map.get("userId"),
                        map -> ((Number) map.get("totalAmount")).doubleValue()
                ));

        Set<UUID> allUserIds = new HashSet<>(expMap.keySet());

        return allUserIds.stream().map(userId -> {
            double totalExp = expMap.getOrDefault(userId, 0.0);
            int currentLevel = levelService.calculateLevel(totalExp);
            Double expForNextLevel = levelService.getExpForNextLevel(currentLevel);
            return new GamificationStatsDTO(userId.toString(), currentLevel, totalExp, expForNextLevel, null);
        }).collect(Collectors.toList());
    }

    public List<GamificationStatsDTO> getUsersGamificationStatsByUserIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> usersExp = userProgressRepository.sumExpAmountGroupedByUsers(userIds);

        Map<UUID, Double> expMap = usersExp.stream()
                .collect(Collectors.toMap(
                        map -> (UUID) map.get("userId"),
                        map -> ((Number) map.get("totalAmount")).doubleValue()
                ));

        return userIds.stream().map(userId -> {
            double totalExp = expMap.getOrDefault(userId, 0.0);
            int currentLevel = levelService.calculateLevel(totalExp);
            Double expForNextLevel = levelService.getExpForNextLevel(currentLevel);
            return new GamificationStatsDTO(userId.toString(), currentLevel, totalExp, expForNextLevel, null);
        }).collect(Collectors.toList());
    }
}
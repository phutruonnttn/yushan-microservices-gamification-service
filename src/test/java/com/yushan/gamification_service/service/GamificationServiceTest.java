package com.yushan.gamification_service.service;

import com.yushan.gamification_service.repository.UserProgressRepository;
import com.yushan.gamification_service.dto.achievement.AchievementDTO;
import com.yushan.gamification_service.dto.common.PageResponseDTO;
import com.yushan.gamification_service.dto.event.LevelUpEvent;
import com.yushan.gamification_service.dto.stats.GamificationStatsDTO;
import com.yushan.gamification_service.dto.transaction.AdminYuanTransactionDTO;
import com.yushan.gamification_service.dto.vote.VoteCheckResponseDTO;
import com.yushan.gamification_service.entity.*;
import com.yushan.gamification_service.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GamificationServiceTest {

    @Mock
    private UserProgressRepository userProgressRepository;

    @Mock
    private LevelService levelService;

    @Mock
    private AchievementService achievementService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private GamificationService gamificationService;

    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        ReflectionTestUtils.setField(gamificationService, "dailyLoginExp", 5.0);
        ReflectionTestUtils.setField(gamificationService, "commentExp", 5.0);
        ReflectionTestUtils.setField(gamificationService, "reviewExp", 5.0);
        ReflectionTestUtils.setField(gamificationService, "voteExp", 3.0);
        ReflectionTestUtils.setField(gamificationService, "registrationYuan", 2.0);
        // Inject UserProgressRepository into GamificationService
        ReflectionTestUtils.setField(gamificationService, "userProgressRepository", userProgressRepository);
    }

    @Test
    void processUserRegistration_Success() {
        // When
        gamificationService.processUserRegistration(testUserId);

        // Then
        ArgumentCaptor<YuanTransaction> captor = ArgumentCaptor.forClass(YuanTransaction.class);
        verify(userProgressRepository).saveYuanTransaction(captor.capture());
        assertEquals(testUserId, captor.getValue().getUserId());
        assertEquals(2.0, captor.getValue().getAmount());
        assertEquals("Registration Reward", captor.getValue().getDescription());
    }

    @Test
    void processUserLogin_FirstLoginOfDay_Success() {
        // Given
        when(userProgressRepository.findDailyRewardLogByUserId(testUserId)).thenReturn(Optional.empty());
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(0.0);
        when(levelService.calculateLevel(anyDouble())).thenReturn(1);

        // When
        gamificationService.processUserLogin(testUserId);

        // Then
        verify(userProgressRepository).saveExpTransaction(any(ExpTransaction.class));
        verify(userProgressRepository).saveYuanTransaction(any(YuanTransaction.class));
        verify(achievementService).checkAndUnlockLoginAchievements(testUserId);
        verify(userProgressRepository).saveDailyRewardLog(any(DailyRewardLog.class));
        verify(userProgressRepository, never()).updateDailyRewardLog(any());
    }

    @Test
    void processUserLogin_PreviousDayLogin_Success() {
        // Given
        DailyRewardLog oldLog = new DailyRewardLog();
        oldLog.setUserId(testUserId);
        oldLog.setLastRewardDate(LocalDate.now().minusDays(1));
        when(userProgressRepository.findDailyRewardLogByUserId(testUserId)).thenReturn(Optional.of(oldLog));
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(10.0);
        when(levelService.calculateLevel(anyDouble())).thenReturn(1);

        // When
        gamificationService.processUserLogin(testUserId);

        // Then
        verify(userProgressRepository).saveExpTransaction(any(ExpTransaction.class));
        verify(userProgressRepository).saveYuanTransaction(any(YuanTransaction.class));
        verify(userProgressRepository).updateDailyRewardLog(any(DailyRewardLog.class));
        verify(userProgressRepository, never()).saveDailyRewardLog(any());
    }

    @Test
    void processUserLogin_AlreadyLoggedInToday_NoReward() {
        // Given
        DailyRewardLog existingLog = new DailyRewardLog();
        existingLog.setUserId(testUserId);
        existingLog.setLastRewardDate(LocalDate.now());
        when(userProgressRepository.findDailyRewardLogByUserId(testUserId)).thenReturn(Optional.of(existingLog));

        // When
        gamificationService.processUserLogin(testUserId);

        // Then
        verify(userProgressRepository, never()).saveExpTransaction(any());
        verify(userProgressRepository, never()).saveYuanTransaction(any());
        verify(achievementService).checkAndUnlockLoginAchievements(testUserId);
    }

    @Test
    void processUserLogin_LevelUp_EventPublished() {
        // Given
        when(userProgressRepository.findDailyRewardLogByUserId(testUserId)).thenReturn(Optional.empty());
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(98.0, 103.0); // Before and after
        when(levelService.calculateLevel(98.0)).thenReturn(1);
        when(levelService.calculateLevel(103.0)).thenReturn(2);

        // When
        gamificationService.processUserLogin(testUserId);

        // Then
        verify(kafkaTemplate).send(eq("internal_gamification_events"), any(LevelUpEvent.class));
    }

    @Test
    void processUserComment_Success() {
        // Given
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(10.0, 15.0);
        when(levelService.calculateLevel(anyDouble())).thenReturn(1);

        // When
        gamificationService.processUserComment(testUserId, 1L);

        // Then
        verify(userProgressRepository).saveExpTransaction(any(ExpTransaction.class));
        verify(achievementService).checkAndUnlockCommentAchievements(testUserId, 1L);
        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void processUserReview_Success() {
        // Given
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(10.0, 15.0);
        when(levelService.calculateLevel(anyDouble())).thenReturn(1);

        // When
        gamificationService.processUserReview(testUserId, 1L);

        // Then
        verify(userProgressRepository).saveExpTransaction(any(ExpTransaction.class));
        verify(achievementService).checkAndUnlockReviewAchievements(testUserId, 1L);
    }

    @Test
    void processUserVote_Success() {
        // Given
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(10.0, 13.0);
        when(levelService.calculateLevel(anyDouble())).thenReturn(1);

        // When
        gamificationService.processUserVote(testUserId);

        // Then
        verify(userProgressRepository).saveExpTransaction(any(ExpTransaction.class));
        verify(userProgressRepository).saveYuanTransaction(any(YuanTransaction.class));
    }

    @Test
    void getGamificationStatsForUser_WithData() {
        // Given
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(150.0);
        when(userProgressRepository.sumYuanAmountByUserId(testUserId)).thenReturn(25.5);
        when(levelService.calculateLevel(150.0)).thenReturn(2);
        when(levelService.getExpForNextLevel(2)).thenReturn(500.0);

        // When
        GamificationStatsDTO stats = gamificationService.getGamificationStatsForUser(testUserId);

        // Then
        assertEquals(150.0, stats.getCurrentExp());
        assertEquals(500.0, stats.getTotalExpForNextLevel());
        assertEquals(25.5, stats.getYuanBalance());
    }

    @Test
    void getGamificationStatsForUser_NoData() {
        // Given
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(null);
        when(userProgressRepository.sumYuanAmountByUserId(testUserId)).thenReturn(null);
        when(levelService.calculateLevel(0.0)).thenReturn(1);
        when(levelService.getExpForNextLevel(1)).thenReturn(100.0);

        // When
        GamificationStatsDTO stats = gamificationService.getGamificationStatsForUser(testUserId);

        // Then
        assertEquals(0.0, stats.getCurrentExp());
        assertEquals(100.0, stats.getTotalExpForNextLevel());
        assertEquals(0.0, stats.getYuanBalance());
    }

    @Test
    void getTransactionHistory_Success() {
        // Given
        YuanTransaction transaction = new YuanTransaction();
        transaction.setAmount(10.0);
        transaction.setDescription("Test");
        transaction.setCreatedAt(OffsetDateTime.now());
        when(userProgressRepository.findYuanTransactionsByUserIdPaged(testUserId, 0, 10)).thenReturn(Collections.singletonList(transaction));

        // When
        var history = gamificationService.getTransactionHistory(testUserId, 0, 10);

        // Then
        assertEquals(1, history.size());
        assertEquals(10.0, history.get(0).getAmount());
    }

    @Test
    void getUnlockedAchievements_Success() {
        // Given
        when(userProgressRepository.findUnlockedAchievementsByUserId(testUserId)).thenReturn(Collections.singletonList(new AchievementDTO()));

        // When
        List<AchievementDTO> achievements = gamificationService.getUnlockedAchievements(testUserId);

        // Then
        assertEquals(1, achievements.size());
        verify(userProgressRepository).findUnlockedAchievementsByUserId(testUserId);
    }

    @Test
    void findYuanTransactionsForAdmin_Success() {
        // Given
        OffsetDateTime now = OffsetDateTime.now();
        when(userProgressRepository.findYuanTransactionsWithFilters(testUserId, now, now, 0, 10)).thenReturn(Collections.singletonList(new YuanTransaction()));
        when(userProgressRepository.countYuanTransactionsWithFilters(testUserId, now, now)).thenReturn(1L);

        // When
        PageResponseDTO<AdminYuanTransactionDTO> result = gamificationService.findYuanTransactionsForAdmin(testUserId, now, now, 0, 10);

        // Then
        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void rewardComment_Success() {
        // Given
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(10.0, 15.0);
        when(levelService.calculateLevel(anyDouble())).thenReturn(1);

        // When
        gamificationService.rewardComment(testUserId, 1L);

        // Then
        verify(userProgressRepository).saveExpTransaction(any(ExpTransaction.class));
    }

    @Test
    void rewardReview_Success() {
        // Given
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(10.0, 15.0);
        when(levelService.calculateLevel(anyDouble())).thenReturn(1);

        // When
        gamificationService.rewardReview(testUserId, 1L);

        // Then
        verify(userProgressRepository).saveExpTransaction(any(ExpTransaction.class));
    }

    @Test
    void rewardVote_Success() {
        // Given
        when(userProgressRepository.sumExpAmountByUserId(testUserId)).thenReturn(10.0, 13.0);
        when(levelService.calculateLevel(anyDouble())).thenReturn(1);

        // When
        gamificationService.rewardVote(testUserId);

        // Then
        verify(userProgressRepository).saveExpTransaction(any(ExpTransaction.class));
    }

    @Test
    void checkVoteEligibility_CanVote() {
        // Given
        when(userProgressRepository.sumYuanAmountByUserId(testUserId)).thenReturn(10.0);

        // When
        VoteCheckResponseDTO response = gamificationService.checkVoteEligibility(testUserId);

        // Then
        assertTrue(response.isCanVote());
        assertEquals(10.0, response.getCurrentYuanBalance());
    }

    @Test
    void checkVoteEligibility_CannotVote() {
        // Given
        when(userProgressRepository.sumYuanAmountByUserId(testUserId)).thenReturn(0.5);

        // When
        VoteCheckResponseDTO response = gamificationService.checkVoteEligibility(testUserId);

        // Then
        assertFalse(response.isCanVote());
    }

    @Test
    void checkVoteEligibility_NullBalance() {
        // Given
        when(userProgressRepository.sumYuanAmountByUserId(testUserId)).thenReturn(null);

        // When
        VoteCheckResponseDTO response = gamificationService.checkVoteEligibility(testUserId);

        // Then
        assertFalse(response.isCanVote());
        assertEquals(0.0, response.getCurrentYuanBalance());
    }

    @Test
    void updateYuanAfterVote_Success() {
        // When
        gamificationService.updateYuanAfterVote(testUserId);

        // Then
        ArgumentCaptor<YuanTransaction> captor = ArgumentCaptor.forClass(YuanTransaction.class);
        verify(userProgressRepository).saveYuanTransaction(captor.capture());
        assertEquals(-1.0, captor.getValue().getAmount());
        assertEquals("Vote cost", captor.getValue().getDescription());
    }

    @Test
    void adminAddYuan_Success() {
        // When
        gamificationService.adminAddYuan(testUserId, 100.0, "Test reason");

        // Then
        ArgumentCaptor<YuanTransaction> captor = ArgumentCaptor.forClass(YuanTransaction.class);
        verify(userProgressRepository).saveYuanTransaction(captor.capture());
        assertEquals(100.0, captor.getValue().getAmount());
        assertEquals("Test reason", captor.getValue().getDescription());
    }

    @Test
    void adminAddYuan_InvalidAmount_ThrowsException() {
        // When & Then
        assertThrows(ValidationException.class, () -> {
            gamificationService.adminAddYuan(testUserId, 0.0, "Invalid");
        });
        verify(userProgressRepository, never()).saveYuanTransaction(any());
    }

    @Test
    void getAllUsersGamificationStats_Success() {
        // Given
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        Map<String, Object> user1Exp = new HashMap<>();
        user1Exp.put("userId", user1);
        user1Exp.put("totalAmount", 150.0);

        Map<String, Object> user2Exp = new HashMap<>();
        user2Exp.put("userId", user2);
        user2Exp.put("totalAmount", 250.0);

        Map<String, Object> user1Yuan = new HashMap<>();
        user1Yuan.put("userId", user1);
        user1Yuan.put("totalAmount", 20.0);
        // User2 has no yuan transaction for testing purposes

        when(userProgressRepository.sumExpAmountGroupedByUser()).thenReturn(Arrays.asList(user1Exp, user2Exp));

        when(levelService.calculateLevel(150.0)).thenReturn(2);
        when(levelService.getExpForNextLevel(2)).thenReturn(500.0);
        when(levelService.calculateLevel(250.0)).thenReturn(3);
        when(levelService.getExpForNextLevel(3)).thenReturn(2000.0);

        // When
        List<GamificationStatsDTO> result = gamificationService.getAllUsersGamificationStats();

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto ->
                dto.getUserId().equals(user1.toString()) &&
                        dto.getLevel() == 2 &&
                        dto.getCurrentExp() == 150.0 &&
                        dto.getTotalExpForNextLevel() == 500.0
        ));
        assertTrue(result.stream().anyMatch(dto ->
                dto.getUserId().equals(user2.toString()) &&
                        dto.getLevel() == 3 &&
                        dto.getCurrentExp() == 250.0 &&
                        dto.getTotalExpForNextLevel() == 2000.0
        ));

        verify(userProgressRepository).sumExpAmountGroupedByUser();
    }

    @Test
    void getUsersGamificationStatsByUserIds_Success() {
        // Given
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        List<UUID> userIds = Arrays.asList(user1, user2);

        Map<String, Object> user1Exp = new HashMap<>();
        user1Exp.put("userId", user1);
        user1Exp.put("totalAmount", 150.0);

        Map<String, Object> user1Yuan = new HashMap<>();
        user1Yuan.put("userId", user1);
        user1Yuan.put("totalAmount", 20.0);

        // Mocking so that user2 has exp but no yuan
        when(userProgressRepository.sumExpAmountGroupedByUsers(userIds)).thenReturn(Collections.singletonList(user1Exp));

        when(levelService.calculateLevel(150.0)).thenReturn(2);
        when(levelService.getExpForNextLevel(2)).thenReturn(500.0);
        when(levelService.calculateLevel(0.0)).thenReturn(1); // For user2 with 0 exp
        when(levelService.getExpForNextLevel(1)).thenReturn(100.0);

        // When
        List<GamificationStatsDTO> result = gamificationService.getUsersGamificationStatsByUserIds(userIds);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto ->
                dto.getUserId().equals(user1.toString()) &&
                        dto.getLevel() == 2 &&
                        dto.getCurrentExp() == 150.0 &&
                        dto.getTotalExpForNextLevel() == 500.0
        ));
        assertTrue(result.stream().anyMatch(dto ->
                dto.getUserId().equals(user2.toString()) &&
                        dto.getLevel() == 1 &&
                        dto.getCurrentExp() == 0.0 && // Default value
                        dto.getTotalExpForNextLevel() == 100.0
        ));

        verify(userProgressRepository).sumExpAmountGroupedByUsers(userIds);
    }

    @Test
    void getUsersGamificationStatsByUserIds_EmptyList_ReturnsEmpty() {
        // Given
        List<UUID> userIds = Collections.emptyList();

        // When
        List<GamificationStatsDTO> result = gamificationService.getUsersGamificationStatsByUserIds(userIds);

        // Then
        assertTrue(result.isEmpty());
        verify(userProgressRepository, never()).sumExpAmountGroupedByUsers(any());
    }

}

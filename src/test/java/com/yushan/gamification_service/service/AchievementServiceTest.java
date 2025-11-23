package com.yushan.gamification_service.service;

import com.yushan.gamification_service.repository.UserProgressRepository;
import org.springframework.test.util.ReflectionTestUtils;
import com.yushan.gamification_service.entity.UserAchievement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AchievementServiceTest {

    @Mock(lenient = true)
    private UserProgressRepository userProgressRepository;

    @InjectMocks
    private AchievementService achievementService;

    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        // Inject UserProgressRepository into AchievementService
        ReflectionTestUtils.setField(achievementService, "userProgressRepository", userProgressRepository);
    }

    @Test
    void checkAndUnlockLoginAchievements_FirstLogin_UnlocksAchievement() {
        // Given
        when(userProgressRepository.findUserAchievementByUserIdAndAchievementId(eq(testUserId), eq("WELCOME_TO_YUSHAN")))
            .thenReturn(null);

        // When
        achievementService.checkAndUnlockLoginAchievements(testUserId);

        // Then
        verify(userProgressRepository).saveUserAchievement(any(UserAchievement.class));
    }

    @Test
    void checkAndUnlockLoginAchievements_AlreadyUnlocked_DoesNothing() {
        // Given
        when(userProgressRepository.findUserAchievementByUserIdAndAchievementId(eq(testUserId), eq("WELCOME_TO_YUSHAN")))
            .thenReturn(1L);

        // When
        achievementService.checkAndUnlockLoginAchievements(testUserId);

        // Then
        verify(userProgressRepository, never()).saveUserAchievement(any(UserAchievement.class));
    }

    @ParameterizedTest
    @CsvSource({
        "1, FIRST_CRY, true",
        "5, FIRST_CRY, false",
        "10, ELOQUENT_SPEAKER, true",
        "25, ELOQUENT_SPEAKER, false",
        "50, COMMENT_MASTER, true",
        "100, COMMENT_MASTER, false"
    })
    void checkAndUnlockCommentAchievements_UnlocksCorrectAchievements(
            long commentCount, String achievementId, boolean shouldUnlock) {
        // Given
        when(userProgressRepository.findUserAchievementByUserIdAndAchievementId(eq(testUserId), eq(achievementId)))
                .thenReturn(shouldUnlock ? null : 1L);

        // When
        achievementService.checkAndUnlockCommentAchievements(testUserId, commentCount);

        // Then
        if (shouldUnlock) {
            verify(userProgressRepository).findUserAchievementByUserIdAndAchievementId(eq(testUserId), eq(achievementId));
            verify(userProgressRepository).saveUserAchievement(any(UserAchievement.class));
        } else {
            verify(userProgressRepository, never()).saveUserAchievement(any(UserAchievement.class));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, REVIEW_ROOKIE, true",
        "5, REVIEW_ROOKIE, false",
        "10, INSIGHTFUL_CRITIC, true",
        "25, INSIGHTFUL_CRITIC, false",
        "50, LITERARY_GURU, true",
        "100, LITERARY_GURU, false"
    })
    void checkAndUnlockReviewAchievements_UnlocksCorrectAchievements(
            long reviewCount, String achievementId, boolean shouldUnlock) {
        // Given
        when(userProgressRepository.findUserAchievementByUserIdAndAchievementId(eq(testUserId), eq(achievementId)))
            .thenReturn(shouldUnlock ? null : 1L);

        // When
        achievementService.checkAndUnlockReviewAchievements(testUserId, reviewCount);

        // Then
        if (shouldUnlock) {
            verify(userProgressRepository).findUserAchievementByUserIdAndAchievementId(eq(testUserId), eq(achievementId));
            verify(userProgressRepository).saveUserAchievement(any(UserAchievement.class));
        } else {
            verify(userProgressRepository, never()).saveUserAchievement(any(UserAchievement.class));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, SHARP_EYE, true",
        "5, SHARP_EYE, false",
        "10, TASTE_MAKER, true",
        "15, TASTE_MAKER, false"
    })
    void checkAndUnlockVoteAchievements_UnlocksCorrectAchievements(
            long voteCount, String achievementId, boolean shouldUnlock) {
        // Given
        when(userProgressRepository.findUserAchievementByUserIdAndAchievementId(eq(testUserId), eq(achievementId)))
            .thenReturn(shouldUnlock ? null : 1L);

        // When
        achievementService.checkAndUnlockVoteAchievements(testUserId, voteCount);

        // Then
        if (shouldUnlock) {
            verify(userProgressRepository).findUserAchievementByUserIdAndAchievementId(eq(testUserId), eq(achievementId));
            verify(userProgressRepository).saveUserAchievement(any(UserAchievement.class));
        } else {
            verify(userProgressRepository, never()).saveUserAchievement(any(UserAchievement.class));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "3, GETTING_GOOD, true",
        "4, GETTING_GOOD, false",
        "5, ACCOMPLISHED_SCHOLAR, true",
        "6, ACCOMPLISHED_SCHOLAR, false"
    })
    void checkAndUnlockLevelAchievements_UnlocksCorrectAchievements(
            int level, String achievementId, boolean shouldUnlock) {
        // Given
        when(userProgressRepository.findUserAchievementByUserIdAndAchievementId(eq(testUserId), eq(achievementId)))
            .thenReturn(shouldUnlock ? null : 1L);

        // When
        achievementService.checkAndUnlockLevelAchievements(testUserId, level);

        // Then
        if (shouldUnlock) {
            verify(userProgressRepository).findUserAchievementByUserIdAndAchievementId(eq(testUserId), eq(achievementId));
            verify(userProgressRepository).saveUserAchievement(any(UserAchievement.class));
        } else {
            verify(userProgressRepository, never()).saveUserAchievement(any(UserAchievement.class));
        }
    }
}

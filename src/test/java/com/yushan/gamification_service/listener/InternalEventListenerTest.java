package com.yushan.gamification_service.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yushan.gamification_service.dto.event.LevelUpEvent;
import com.yushan.gamification_service.service.AchievementService;
import com.yushan.gamification_service.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalEventListenerTest {

    @Mock
    private AchievementService achievementService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private InternalEventListener internalEventListener;

    @Test
    void handleLevelUpEvent_shouldCheckAndUnlockAchievements() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        int newLevel = 5;
        LevelUpEvent event = new LevelUpEvent(userId, newLevel);
        String message = "{\"userId\":\"" + userId + "\",\"newLevel\":5}";

        when(objectMapper.readValue(message, LevelUpEvent.class)).thenReturn(event);
        when(idempotencyService.isProcessed(anyString(), eq("LevelUpAchievement"))).thenReturn(false); // Not processed yet
        doNothing().when(idempotencyService).markAsProcessed(anyString(), eq("LevelUpAchievement"));
        doNothing().when(achievementService).checkAndUnlockLevelAchievements(userId, newLevel);

        // When
        internalEventListener.handleLevelUpEvent(message);

        // Then
        verify(objectMapper).readValue(message, LevelUpEvent.class);
        verify(idempotencyService).isProcessed(anyString(), eq("LevelUpAchievement"));
        verify(achievementService).checkAndUnlockLevelAchievements(userId, newLevel);
        verify(idempotencyService).markAsProcessed(anyString(), eq("LevelUpAchievement"));
    }

    @Test
    void handleLevelUpEvent_shouldHandleJsonProcessingException() throws Exception {
        // Given
        String invalidMessage = "invalid-json";
        when(objectMapper.readValue(invalidMessage, LevelUpEvent.class))
                .thenThrow(new JsonProcessingException("Test Exception") {});

        // When & Then
        try {
            internalEventListener.handleLevelUpEvent(invalidMessage);
        } catch (RuntimeException e) {
            // Expected: RuntimeException is thrown to trigger Kafka retry
        }

        // Then
        // Verify that the service method was not called due to the exception
        verify(achievementService, never()).checkAndUnlockLevelAchievements(any(UUID.class), anyInt());
    }
}


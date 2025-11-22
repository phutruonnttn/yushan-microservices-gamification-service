package com.yushan.gamification_service.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yushan.gamification_service.service.GamificationService;
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
class EngagementEventListenerTest {

    @Mock
    private GamificationService gamificationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JsonNode jsonNode;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private EngagementEventListener engagementEventListener;

    private final UUID testUserId = UUID.randomUUID();
    private final String testUserIdStr = testUserId.toString();

    @Test
    void handleCommentCreatedEvent_shouldProcessComment() throws Exception {
        // Given
        long commentId = 123L;
        String eventJson = String.format("{\"commentId\":%d,\"userId\":\"%s\"}", commentId, testUserIdStr);

        JsonNode commentIdNode = mock(JsonNode.class);
        JsonNode userIdNode = mock(JsonNode.class);
        when(objectMapper.readTree(eventJson)).thenReturn(jsonNode);
        when(jsonNode.get("commentId")).thenReturn(commentIdNode);
        when(commentIdNode.asInt()).thenReturn((int) commentId);
        when(jsonNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn(testUserIdStr);
        when(idempotencyService.isProcessed(anyString(), eq("CommentReward"))).thenReturn(false); // Not processed yet
        doNothing().when(idempotencyService).markAsProcessed(anyString(), eq("CommentReward"));
        doNothing().when(gamificationService).processUserComment(testUserId, commentId);

        // When
        engagementEventListener.handleCommentCreatedEvent(eventJson);

        // Then
        verify(objectMapper).readTree(eventJson);
        verify(idempotencyService).isProcessed(anyString(), eq("CommentReward"));
        verify(gamificationService).processUserComment(testUserId, commentId);
        verify(idempotencyService).markAsProcessed(anyString(), eq("CommentReward"));
    }

    @Test
    void handleReviewCreatedEvent_shouldProcessReview() throws Exception {
        // Given
        long reviewId = 456L;
        String eventJson = String.format("{\"reviewId\":%d,\"userId\":\"%s\"}", reviewId, testUserIdStr);

        JsonNode reviewIdNode = mock(JsonNode.class);
        JsonNode userIdNode = mock(JsonNode.class);
        when(objectMapper.readTree(eventJson)).thenReturn(jsonNode);
        when(jsonNode.get("reviewId")).thenReturn(reviewIdNode);
        when(reviewIdNode.asInt()).thenReturn((int) reviewId);
        when(jsonNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn(testUserIdStr);
        when(idempotencyService.isProcessed(anyString(), eq("ReviewReward"))).thenReturn(false); // Not processed yet
        doNothing().when(idempotencyService).markAsProcessed(anyString(), eq("ReviewReward"));
        doNothing().when(gamificationService).processUserReview(testUserId, reviewId);

        // When
        engagementEventListener.handleReviewCreatedEvent(eventJson);

        // Then
        verify(objectMapper).readTree(eventJson);
        verify(idempotencyService).isProcessed(anyString(), eq("ReviewReward"));
        verify(gamificationService).processUserReview(testUserId, reviewId);
        verify(idempotencyService).markAsProcessed(anyString(), eq("ReviewReward"));
    }

    @Test
    void handleVoteCreatedEvent_shouldProcessVote() throws Exception {
        // Given
        Integer voteId = 789;
        String eventJson = String.format("{\"voteId\":%d,\"userId\":\"%s\"}", voteId, testUserIdStr);
        
        JsonNode voteIdNode = mock(JsonNode.class);
        JsonNode userIdNode = mock(JsonNode.class);
        when(objectMapper.readTree(eventJson)).thenReturn(jsonNode);
        when(jsonNode.has("voteId")).thenReturn(true);
        when(jsonNode.get("voteId")).thenReturn(voteIdNode);
        when(voteIdNode.asInt()).thenReturn(voteId);
        when(jsonNode.get("userId")).thenReturn(userIdNode);
        when(userIdNode.asText()).thenReturn(testUserIdStr);
        when(idempotencyService.isProcessed(anyString(), eq("VoteReward"))).thenReturn(false); // Not processed yet
        doNothing().when(idempotencyService).markAsProcessed(anyString(), eq("VoteReward"));
        doNothing().when(gamificationService).processUserVote(testUserId);

        // When
        engagementEventListener.handleVoteCreatedEvent(eventJson);

        // Then
        verify(objectMapper).readTree(eventJson);
        verify(idempotencyService).isProcessed(anyString(), eq("VoteReward"));
        verify(gamificationService).processUserVote(testUserId);
        verify(idempotencyService).markAsProcessed(anyString(), eq("VoteReward"));
    }

    @Test
    void handleEvent_shouldCatchJsonProcessingException() throws Exception {
        // Given
        String invalidJson = "invalid-json";
        when(objectMapper.readTree(invalidJson))
                .thenThrow(new JsonProcessingException("Test Exception") {});

        // When & Then
        try {
            engagementEventListener.handleCommentCreatedEvent(invalidJson);
        } catch (RuntimeException e) {
            // Expected: RuntimeException is thrown to trigger Kafka retry
        }

        // Then
        // Verify that the service method was not called due to the exception
        verify(gamificationService, never()).processUserComment(any(), anyLong());
        verify(idempotencyService, never()).isProcessed(anyString(), anyString());
        verify(idempotencyService, never()).markAsProcessed(anyString(), anyString());
    }
}

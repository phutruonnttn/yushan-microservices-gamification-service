package com.yushan.gamification_service.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yushan.gamification_service.dto.event.EventEnvelope;
import com.yushan.gamification_service.dto.event.UserLoggedInEvent;
import com.yushan.gamification_service.dto.event.UserRegisteredEvent;
import com.yushan.gamification_service.service.GamificationService;
import com.yushan.gamification_service.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventListenerTest {

    @Mock
    private GamificationService gamificationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JsonNode jsonNode;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private UserEventListener userEventListener;

    @Test
    void handleUserEvent_shouldProcessUserRegistration() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String message = "{\"eventType\":\"UserRegisteredEvent\", \"payload\":{...}}";
        Date date = new Date();
        UserRegisteredEvent registeredEvent = new UserRegisteredEvent(userId, "", email, date, date, date, date);
        EventEnvelope envelope = new EventEnvelope("UserRegisteredEvent", jsonNode);

        when(objectMapper.readValue(message, EventEnvelope.class)).thenReturn(envelope);
        when(objectMapper.treeToValue(jsonNode, UserRegisteredEvent.class)).thenReturn(registeredEvent);
        when(idempotencyService.isProcessed(anyString(), eq("UserRegistration"))).thenReturn(false); // Not processed yet
        doNothing().when(idempotencyService).markAsProcessed(anyString(), eq("UserRegistration"));
        doNothing().when(gamificationService).processUserRegistration(userId);

        // When
        userEventListener.handleUserEvent(message);

        // Then
        verify(idempotencyService).isProcessed(anyString(), eq("UserRegistration"));
        verify(gamificationService).processUserRegistration(userId);
        verify(idempotencyService).markAsProcessed(anyString(), eq("UserRegistration"));
        verify(gamificationService, never()).processUserLogin(any());
    }

    @Test
    void handleUserEvent_shouldProcessUserLogin() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String message = "{\"eventType\":\"UserLoggedInEvent\", \"payload\":{...}}";
        Date date = new Date();
        UserLoggedInEvent loggedInEvent = new UserLoggedInEvent(userId, "", email, date, date, date, date);
        EventEnvelope envelope = new EventEnvelope("UserLoggedInEvent", jsonNode);

        when(objectMapper.readValue(message, EventEnvelope.class)).thenReturn(envelope);
        when(objectMapper.treeToValue(jsonNode, UserLoggedInEvent.class)).thenReturn(loggedInEvent);
        when(idempotencyService.isProcessed(anyString(), eq("UserLogin"))).thenReturn(false); // Not processed yet
        doNothing().when(idempotencyService).markAsProcessed(anyString(), eq("UserLogin"));
        doNothing().when(gamificationService).processUserLogin(userId);

        // When
        userEventListener.handleUserEvent(message);

        // Then
        verify(idempotencyService).isProcessed(anyString(), eq("UserLogin"));
        verify(gamificationService).processUserLogin(userId);
        verify(idempotencyService).markAsProcessed(anyString(), eq("UserLogin"));
        verify(gamificationService, never()).processUserRegistration(any());
    }

    @Test
    void handleUserEvent_shouldIgnoreUnknownEventType() throws Exception {
        // Given
        String message = "{\"eventType\":\"UnknownEvent\", \"payload\":{...}}";
        EventEnvelope envelope = new EventEnvelope("UnknownEvent", jsonNode);

        when(objectMapper.readValue(message, EventEnvelope.class)).thenReturn(envelope);

        // When
        userEventListener.handleUserEvent(message);

        // Then
        verify(gamificationService, never()).processUserRegistration(any());
        verify(gamificationService, never()).processUserLogin(any());
    }

    @Test
    void handleUserEvent_shouldHandleJsonProcessingException() throws Exception {
        // Given
        String invalidJson = "invalid-json";
        when(objectMapper.readValue(invalidJson, EventEnvelope.class))
                .thenThrow(new JsonProcessingException("Test Exception") {});

        // When & Then
        try {
            userEventListener.handleUserEvent(invalidJson);
        } catch (RuntimeException e) {
            // Expected: RuntimeException is thrown to trigger Kafka retry
        }

        // Then
        verify(gamificationService, never()).processUserRegistration(any());
        verify(gamificationService, never()).processUserLogin(any());
    }
}

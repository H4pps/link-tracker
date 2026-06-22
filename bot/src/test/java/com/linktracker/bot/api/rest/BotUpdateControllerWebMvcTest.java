package com.linktracker.bot.api.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linktracker.bot.api.rest.controllers.BotUpdateController;
import com.linktracker.bot.api.rest.errors.BotApiExceptionHandler;
import com.linktracker.bot.api.rest.interceptors.BotApiLoggingInterceptor;
import com.linktracker.bot.api.rest.ratelimit.IpRateLimitInterceptor;
import com.linktracker.bot.api.rest.ratelimit.Resilience4jIpRateLimiter;
import com.linktracker.bot.application.update.BotUpdateUseCase;
import com.linktracker.bot.logging.BotLogger;
import com.linktracker.bot.properties.ResilienceProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BotUpdateControllerWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private BotUpdateUseCase botUpdateUseCase;

    @Mock
    private BotLogger botLogger;

    @BeforeEach
    void setUp() {
        mockMvc = createMockMvc(rateLimitProperties(1_000, Duration.ofMinutes(1), Duration.ZERO));
    }

    @Test
    void postUpdatesReturnsOkForValidPayload() throws Exception {
        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "id": 10,
                                  "url": "https://github.com/octocat/Hello-World",
                                  "description": "changed",
                                  "tgChatIds": [100, 200]
                                }
                                """))
                .andExpect(status().isOk());

        verify(botUpdateUseCase).processLinkUpdate(any());
        verify(botLogger).logApiRequestReceived("/updates");
        verify(botLogger).logApiRequestSucceeded("/updates", 200);
    }

    @Test
    void postUpdatesRateLimitReturnsTooManyRequests() throws Exception {
        MockMvc limitedMockMvc = createMockMvc(rateLimitProperties(1, Duration.ofMinutes(1), Duration.ZERO));

        limitedMockMvc
                .perform(post("/updates")
                        .header("X-Forwarded-For", "203.0.113.20, 198.51.100.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdatePayload()))
                .andExpect(status().isOk());
        limitedMockMvc
                .perform(post("/updates")
                        .header("X-Forwarded-For", "203.0.113.20, 198.51.100.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdatePayload()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        limitedMockMvc
                .perform(post("/updates")
                        .header("X-Forwarded-For", "203.0.113.21, 198.51.100.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdatePayload()))
                .andExpect(status().isOk());

        verify(botUpdateUseCase, times(2)).processLinkUpdate(any());
    }

    private MockMvc createMockMvc(ResilienceProperties resilienceProperties) {
        return MockMvcBuilders.standaloneSetup(new BotUpdateController(botUpdateUseCase))
                .addInterceptors(new BotApiLoggingInterceptor(botLogger))
                .addInterceptors(new IpRateLimitInterceptor(new Resilience4jIpRateLimiter(resilienceProperties)))
                .setControllerAdvice(new BotApiExceptionHandler(botLogger))
                .build();
    }

    private ResilienceProperties rateLimitProperties(
            int limitForPeriod, Duration limitRefreshPeriod, Duration timeoutDuration) {
        ResilienceProperties properties = new ResilienceProperties();
        properties.rateLimit().setLimitForPeriod(limitForPeriod);
        properties.rateLimit().setLimitRefreshPeriod(limitRefreshPeriod);
        properties.rateLimit().setTimeoutDuration(timeoutDuration);
        return properties;
    }

    private String validUpdatePayload() {
        return """
                {
                  "id": 10,
                  "url": "https://github.com/octocat/Hello-World",
                  "description": "changed",
                  "tgChatIds": [100, 200]
                }
                """;
    }

    @Test
    void postUpdatesReturnsBadRequestWhenRequiredFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "url": "https://github.com/octocat/Hello-World",
                                  "tgChatIds": [100]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.exceptionName").isNotEmpty())
                .andExpect(jsonPath("$.stacktrace").isArray());

        verify(botUpdateUseCase, never()).processLinkUpdate(any());
        verify(botLogger).logApiRequestReceived("/updates");
        verify(botLogger).logApiRequestFailed(eq("/updates"), eq(400), eq("BAD_REQUEST"), any(Exception.class));
        verify(botLogger, never()).logApiRequestSucceeded(anyString(), anyInt());
    }

    @Test
    void postUpdatesReturnsBadRequestForInvalidUrl() throws Exception {
        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "id": 10,
                                  "url": "not-a-uri",
                                  "description": "changed",
                                  "tgChatIds": [100]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(botUpdateUseCase, never()).processLinkUpdate(any());
    }

    @Test
    void postUpdatesReturnsBadRequestForEmptyChatIds() throws Exception {
        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "id": 10,
                                  "url": "https://github.com/octocat/Hello-World",
                                  "description": "changed",
                                  "tgChatIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(botUpdateUseCase, never()).processLinkUpdate(any());
    }

    @Test
    void postUpdatesReturnsInternalServerErrorWhenUseCaseFails() throws Exception {
        doThrow(new IllegalStateException("boom")).when(botUpdateUseCase).processLinkUpdate(any());

        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "id": 10,
                                  "url": "https://github.com/octocat/Hello-World",
                                  "description": "changed",
                                  "tgChatIds": [100]
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        verify(botLogger).logApiRequestReceived("/updates");
        verify(botLogger)
                .logApiRequestFailed(eq("/updates"), eq(500), eq("INTERNAL_SERVER_ERROR"), any(Exception.class));
        verify(botLogger, never()).logApiRequestSucceeded(anyString(), anyInt());
    }
}

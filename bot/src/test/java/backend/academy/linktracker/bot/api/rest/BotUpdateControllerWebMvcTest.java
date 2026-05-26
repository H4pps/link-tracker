package backend.academy.linktracker.bot.api.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.academy.linktracker.bot.api.rest.controllers.BotUpdateController;
import backend.academy.linktracker.bot.api.rest.errors.BotApiExceptionHandler;
import backend.academy.linktracker.bot.api.rest.interceptors.BotApiLoggingInterceptor;
import backend.academy.linktracker.bot.application.update.BotUpdateUseCase;
import backend.academy.linktracker.bot.logging.BotLogger;
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
        mockMvc = MockMvcBuilders.standaloneSetup(new BotUpdateController(botUpdateUseCase))
                .addInterceptors(new BotApiLoggingInterceptor(botLogger))
                .setControllerAdvice(new BotApiExceptionHandler(botLogger))
                .build();
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

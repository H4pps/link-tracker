package backend.academy.linktracker.scrapper.api.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.academy.linktracker.scrapper.api.rest.controllers.LinkController;
import backend.academy.linktracker.scrapper.api.rest.controllers.TgChatController;
import backend.academy.linktracker.scrapper.api.rest.errors.ScrapperApiExceptionHandler;
import backend.academy.linktracker.scrapper.api.rest.interceptors.ScrapperApiLoggingInterceptor;
import backend.academy.linktracker.scrapper.application.chat.ScrapperChatRepository;
import backend.academy.linktracker.scrapper.application.chat.ScrapperChatUseCase;
import backend.academy.linktracker.scrapper.application.chat.ScrapperChatUseCaseImpl;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkRepository;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkUseCase;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkUseCaseImpl;
import backend.academy.linktracker.scrapper.infrastructure.cache.NoOpListLinksCache;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperChatRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperLinkRepository;
import backend.academy.linktracker.scrapper.infrastructure.memory.inmemory.InMemoryScrapperStorage;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class ScrapperApiControllerWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private ScrapperLogger scrapperLogger;

    @BeforeEach
    void setUp() {
        InMemoryScrapperStorage storage = new InMemoryScrapperStorage();
        ScrapperChatRepository chatRepository = new InMemoryScrapperChatRepository(storage);
        ScrapperLinkRepository linkRepository = new InMemoryScrapperLinkRepository(storage);
        NoOpListLinksCache listLinksCache = new NoOpListLinksCache();
        ScrapperChatUseCase scrapperChatUseCase =
                new ScrapperChatUseCaseImpl(chatRepository, listLinksCache, scrapperLogger);
        ScrapperLinkUseCase scrapperLinkUseCase =
                new ScrapperLinkUseCaseImpl(chatRepository, linkRepository, listLinksCache, scrapperLogger);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TgChatController(scrapperChatUseCase), new LinkController(scrapperLinkUseCase))
                .addInterceptors(new ScrapperApiLoggingInterceptor(scrapperLogger))
                .setControllerAdvice(new ScrapperApiExceptionHandler(scrapperLogger))
                .setValidator(validator)
                .build();
    }

    @Test
    void registerAndDeleteChatReturnOkForValidId() throws Exception {
        mockMvc.perform(post("/tg-chat/10")).andExpect(status().isOk());
        mockMvc.perform(delete("/tg-chat/10")).andExpect(status().isOk());
    }

    @Test
    void chatEndpointsReturnBadRequestForInvalidId() throws Exception {
        mockMvc.perform(post("/tg-chat/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(delete("/tg-chat/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void addRemoveAndListLinksReturnContractPayloads() throws Exception {
        mockMvc.perform(post("/tg-chat/1")).andExpect(status().isOk());

        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World",
                                  "tags": ["work"],
                                  "filters": ["f1"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.url").value("https://github.com/octocat/Hello-World"))
                .andExpect(jsonPath("$.tags[0]").value("work"));

        mockMvc.perform(get("/links").header("Tg-Chat-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.links[0].id").value(1))
                .andExpect(jsonPath("$.links[0].url").value("https://github.com/octocat/Hello-World"));

        mockMvc.perform(delete("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.url").value("https://github.com/octocat/Hello-World"));

        mockMvc.perform(get("/links").header("Tg-Chat-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(0));
    }

    @Test
    void listLinksAppliesPaginationQueryParams() throws Exception {
        mockMvc.perform(post("/tg-chat/1")).andExpect(status().isOk());

        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/first",
                                  "tags": ["work"],
                                  "filters": ["f1"]
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/second",
                                  "tags": ["team"],
                                  "filters": ["f2"]
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/third",
                                  "tags": ["study"],
                                  "filters": ["f3"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/links")
                        .header("Tg-Chat-Id", 1)
                        .param("limit", "2")
                        .param("offset", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.links[0].url").value("https://github.com/octocat/second"))
                .andExpect(jsonPath("$.links[1].url").value("https://github.com/octocat/third"));
    }

    @Test
    void listLinksWithoutPaginationParamsKeepsCompatibility() throws Exception {
        mockMvc.perform(post("/tg-chat/1")).andExpect(status().isOk());

        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/a",
                                  "tags": ["one"],
                                  "filters": ["f1"]
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/b",
                                  "tags": ["two"],
                                  "filters": ["f2"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/links").header("Tg-Chat-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.links[0].url").value("https://github.com/octocat/a"))
                .andExpect(jsonPath("$.links[1].url").value("https://github.com/octocat/b"));
    }

    @Test
    void linkEndpointsReturnBadRequestForInvalidHeaderOrBody() throws Exception {
        mockMvc.perform(get("/links").header("Tg-Chat-Id", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"t\"],\"filters\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(delete("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"link\":\"bad-link\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void listLinksRejectsNegativePaginationParams() throws Exception {
        mockMvc.perform(get("/links").header("Tg-Chat-Id", 1).param("limit", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/links").header("Tg-Chat-Id", 1).param("offset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void domainRulesAreMappedToNotFoundAndConflict() throws Exception {
        mockMvc.perform(post("/tg-chat/1")).andExpect(status().isOk());

        mockMvc.perform(post("/tg-chat/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(delete("/tg-chat/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World",
                                  "tags": ["work"],
                                  "filters": ["f1"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World",
                                  "tags": ["work"],
                                  "filters": ["f1"]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(get("/links").header("Tg-Chat-Id", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void linkEndpointsReturnNotFoundForUnknownOrDeletedChat() throws Exception {
        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World",
                                  "tags": ["work"],
                                  "filters": ["f1"]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(delete("/links")
                        .header("Tg-Chat-Id", 999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(post("/tg-chat/1")).andExpect(status().isOk());
        mockMvc.perform(delete("/tg-chat/1")).andExpect(status().isOk());

        mockMvc.perform(get("/links").header("Tg-Chat-Id", 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void removingLinkFromUnknownChatDoesNotAffectExistingChatLinks() throws Exception {
        mockMvc.perform(post("/tg-chat/1")).andExpect(status().isOk());

        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World",
                                  "tags": ["work"],
                                  "filters": ["f1"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/links")
                        .header("Tg-Chat-Id", 999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/links").header("Tg-Chat-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.links[0].url").value("https://github.com/octocat/Hello-World"));
    }

    @Test
    void addLinkReturnsNotFoundWhenChatWasDeleted() throws Exception {
        mockMvc.perform(post("/tg-chat/1")).andExpect(status().isOk());
        mockMvc.perform(delete("/tg-chat/1")).andExpect(status().isOk());

        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World",
                                  "tags": ["work"],
                                  "filters": ["f1"]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void sameLinkCanBeTrackedByDifferentChats() throws Exception {
        mockMvc.perform(post("/tg-chat/1")).andExpect(status().isOk());
        mockMvc.perform(post("/tg-chat/2")).andExpect(status().isOk());

        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World",
                                  "tags": ["work"],
                                  "filters": ["f1"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        mockMvc.perform(post("/links")
                        .header("Tg-Chat-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "link": "https://github.com/octocat/Hello-World",
                                  "tags": ["team"],
                                  "filters": ["f2"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));

        mockMvc.perform(get("/links").header("Tg-Chat-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.links[0].id").value(1));

        mockMvc.perform(get("/links").header("Tg-Chat-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.links[0].id").value(2));
    }
}

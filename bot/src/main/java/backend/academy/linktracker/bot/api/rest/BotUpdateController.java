package backend.academy.linktracker.bot.api.rest;

import backend.academy.linktracker.bot.api.rest.dto.LinkUpdateRequest;
import backend.academy.linktracker.bot.application.update.BotUpdateUseCase;
import backend.academy.linktracker.bot.application.update.LinkUpdateCommand;
import backend.academy.linktracker.bot.telegram.logging.BotLogger;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint that receives link update events from scrapper service.
 */
@RestController
@RequiredArgsConstructor
public class BotUpdateController {

    private static final String UPDATES_ENDPOINT = "/updates";

    private final BotUpdateUseCase botUpdateUseCase;
    private final BotLogger botLogger;

    /**
     * Accepts a link update payload and delegates it to bot update use case.
     *
     * @param request validated update request body
     * @return HTTP 200 for accepted update
     */
    @PostMapping(UPDATES_ENDPOINT)
    public ResponseEntity<Void> postUpdate(@Valid @RequestBody LinkUpdateRequest request) {
        botLogger.logApiUpdateReceived(
                UPDATES_ENDPOINT,
                request.id(),
                request.url(),
                request.tgChatIds().size());

        botUpdateUseCase.processLinkUpdate(toCommand(request));
        botLogger.logApiRequestSucceeded(UPDATES_ENDPOINT, 200);
        return ResponseEntity.ok().build();
    }

    /**
     * Maps transport DTO to application command.
     *
     * @param request validated HTTP request
     * @return application use-case command
     */
    private LinkUpdateCommand toCommand(LinkUpdateRequest request) {
        return new LinkUpdateCommand(
                request.id(), request.url(), request.description(), List.copyOf(request.tgChatIds()));
    }
}

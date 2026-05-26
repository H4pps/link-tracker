package backend.academy.linktracker.bot.api.rest.controllers;

import backend.academy.linktracker.bot.api.rest.update.dto.LinkUpdateRequest;
import backend.academy.linktracker.bot.application.update.BotUpdateUseCase;
import backend.academy.linktracker.bot.application.update.LinkUpdateCommand;
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

    /**
     * Accepts a link update payload and delegates it to bot update use case.
     *
     * @param request validated update request body
     * @return HTTP 200 for accepted update
     */
    @PostMapping(UPDATES_ENDPOINT)
    public ResponseEntity<Void> postUpdate(@Valid @RequestBody LinkUpdateRequest request) {
        botUpdateUseCase.processLinkUpdate(toCommand(request));
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

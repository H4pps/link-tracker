package backend.academy.linktracker.scrapper.api.rest;

import backend.academy.linktracker.scrapper.api.rest.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.api.rest.dto.LinkResponse;
import backend.academy.linktracker.scrapper.api.rest.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.api.rest.dto.RemoveLinkRequest;
import backend.academy.linktracker.scrapper.application.link.AddLinkCommand;
import backend.academy.linktracker.scrapper.application.link.LinkView;
import backend.academy.linktracker.scrapper.application.link.RemoveLinkCommand;
import backend.academy.linktracker.scrapper.application.link.ScrapperLinkUseCase;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for tracked links management.
 */
@RestController
@RequiredArgsConstructor
public class LinkController {

    private static final String LINKS_ENDPOINT = "/links";
    private static final String TG_CHAT_ID_HEADER = "Tg-Chat-Id";

    private final ScrapperLinkUseCase scrapperLinkUseCase;
    private final ScrapperLogger scrapperLogger;

    /**
     * Returns tracked links for a chat.
     *
     * @param chatId positive telegram chat identifier
     * @return list response with size metadata
     */
    @GetMapping(LINKS_ENDPOINT)
    public ResponseEntity<ListLinksResponse> listLinks(@RequestHeader(TG_CHAT_ID_HEADER) @Positive long chatId) {
        scrapperLogger.logRequestReceived(LINKS_ENDPOINT, chatId, null);
        List<LinkResponse> links = scrapperLinkUseCase.listLinks(chatId).stream()
                .map(this::toResponse)
                .toList();
        ListLinksResponse response = new ListLinksResponse(links, links.size());
        scrapperLogger.logRequestSucceeded(LINKS_ENDPOINT, 200);
        return ResponseEntity.ok(response);
    }

    /**
     * Adds a tracked link for a chat.
     *
     * @param chatId positive telegram chat identifier
     * @param request validated add-link payload
     * @return created tracked-link payload
     */
    @PostMapping(LINKS_ENDPOINT)
    public ResponseEntity<LinkResponse> addLink(
            @RequestHeader(TG_CHAT_ID_HEADER) @Positive long chatId, @Valid @RequestBody AddLinkRequest request) {
        scrapperLogger.logRequestReceived(LINKS_ENDPOINT, chatId, request.link());
        AddLinkCommand command =
                new AddLinkCommand(request.link(), toSafeList(request.tags()), toSafeList(request.filters()));
        LinkView addedLink = scrapperLinkUseCase.addLink(chatId, command);
        scrapperLogger.logRequestSucceeded(LINKS_ENDPOINT, 200);
        return ResponseEntity.ok(toResponse(addedLink));
    }

    /**
     * Removes tracked link for a chat.
     *
     * @param chatId positive telegram chat identifier
     * @param request validated remove-link payload
     * @return removed tracked-link payload
     */
    @DeleteMapping(LINKS_ENDPOINT)
    public ResponseEntity<LinkResponse> removeLink(
            @RequestHeader(TG_CHAT_ID_HEADER) @Positive long chatId, @Valid @RequestBody RemoveLinkRequest request) {
        scrapperLogger.logRequestReceived(LINKS_ENDPOINT, chatId, request.link());
        LinkView removedLink = scrapperLinkUseCase.removeLink(chatId, new RemoveLinkCommand(request.link()));
        scrapperLogger.logRequestSucceeded(LINKS_ENDPOINT, 200);
        return ResponseEntity.ok(toResponse(removedLink));
    }

    private LinkResponse toResponse(LinkView linkView) {
        return new LinkResponse(
                linkView.id(), linkView.url(), List.copyOf(linkView.tags()), List.copyOf(linkView.filters()));
    }

    private List<String> toSafeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return List.copyOf(values);
    }
}

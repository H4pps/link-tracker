package com.linktracker.bot.application.track.service;

import com.linktracker.bot.application.scrapper.ScrapperLinkGateway;
import com.linktracker.bot.application.scrapper.command.AddScrapperLinkCommand;
import com.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import com.linktracker.bot.application.scrapper.exception.ScrapperGatewayException;
import com.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import com.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import com.linktracker.bot.application.track.parsing.TrackDialogValueParser;
import com.linktracker.bot.application.track.state.TrackDialogSession;
import com.linktracker.bot.application.track.state.TrackDialogState;
import com.linktracker.bot.application.track.state.TrackDialogStateRepository;
import com.linktracker.bot.application.track.validation.TrackUrlValidator;
import com.linktracker.bot.logging.BotLogger;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Application service for chat-scoped `/track` dialog state machine.
 */
@Component
@RequiredArgsConstructor
public class TrackDialogService {

    public static final String TRACK_REPLY = "Send the link you want to track. Use /cancel to cancel.";
    public static final String TAGS_REPLY = "Send tags separated by commas (or send an empty message).";
    public static final String FILTERS_REPLY = "Send filters separated by commas (or send an empty message).";
    public static final String CANCELLED_REPLY = "Link tracking cancelled.";
    public static final String DUPLICATE_REPLY = "Link is already tracked";
    public static final String INVALID_URL_REPLY =
            "Invalid link. Only GitHub repositories and StackOverflow questions are supported.";
    public static final String SCRAPPER_UNAVAILABLE_REPLY =
            "Scrapper service is temporarily unavailable. Try again later.";
    public static final String CHAT_NOT_REGISTERED_REPLY = "Chat is not registered. Use /start.";
    private static final Map<Class<? extends ScrapperGatewayException>, String> SCRAPPER_ERROR_REPLIES = Map.of(
            ScrapperConflictException.class, DUPLICATE_REPLY,
            ScrapperNotFoundException.class, CHAT_NOT_REGISTERED_REPLY,
            ScrapperUnavailableException.class, SCRAPPER_UNAVAILABLE_REPLY);

    private final TrackDialogStateRepository stateRepository;
    private final ScrapperLinkGateway scrapperGateway;
    private final TrackUrlValidator trackUrlValidator;
    private final TrackDialogValueParser valueParser;
    private final BotLogger botLogger;

    /**
     * Starts `/track` dialog for chat.
     *
     * @param chatId telegram chat identifier
     * @return prompt requesting link input
     */
    public String start(long chatId) {
        stateRepository.save(chatId, new TrackDialogSession(TrackDialogState.AWAITING_LINK, "", List.of()));
        botLogger.logTrackDialogState(chatId, TrackDialogState.AWAITING_LINK.name());
        return TRACK_REPLY;
    }

    /**
     * Cancels `/track` dialog for chat.
     *
     * @param chatId telegram chat identifier
     * @return cancel acknowledgement
     */
    public String cancel(long chatId) {
        stateRepository.clear(chatId);
        botLogger.logTrackDialogState(chatId, TrackDialogState.IDLE.name());
        return CANCELLED_REPLY;
    }

    /**
     * Checks whether chat currently has active dialog.
     *
     * @param chatId telegram chat identifier
     * @return true when dialog is non-idle
     */
    public boolean hasActiveDialog(long chatId) {
        return stateRepository.findByChatId(chatId).state() != TrackDialogState.IDLE;
    }

    /**
     * Handles plain-text message while chat is inside `/track` dialog.
     *
     * @param chatId telegram chat identifier
     * @param messageText raw incoming message text
     * @return bot reply based on current dialog state
     */
    public String handleDialogInput(long chatId, String messageText) {
        TrackDialogSession session = stateRepository.findByChatId(chatId);
        return switch (session.state()) {
            case AWAITING_LINK -> handleLink(chatId, messageText);
            case AWAITING_TAGS -> handleTags(chatId, session, messageText);
            case AWAITING_FILTERS -> handleFilters(chatId, session, messageText);
            case IDLE -> TRACK_REPLY;
        };
    }

    private String handleLink(long chatId, String messageText) {
        String candidate = normalize(messageText);
        if (!trackUrlValidator.isValid(candidate)) {
            return INVALID_URL_REPLY;
        }

        stateRepository.save(chatId, new TrackDialogSession(TrackDialogState.AWAITING_TAGS, candidate, List.of()));
        botLogger.logTrackDialogState(chatId, TrackDialogState.AWAITING_TAGS.name());
        return TAGS_REPLY;
    }

    private String handleTags(long chatId, TrackDialogSession session, String messageText) {
        List<String> tags = valueParser.parseList(messageText);
        stateRepository.save(chatId, new TrackDialogSession(TrackDialogState.AWAITING_FILTERS, session.url(), tags));
        botLogger.logTrackDialogState(chatId, TrackDialogState.AWAITING_FILTERS.name());
        return FILTERS_REPLY;
    }

    private String handleFilters(long chatId, TrackDialogSession session, String messageText) {
        List<String> filters = valueParser.parseList(messageText);
        try {
            scrapperGateway.addLink(chatId, new AddScrapperLinkCommand(session.url(), session.tags(), filters));
            stateRepository.clear(chatId);
            botLogger.logTrackDialogState(chatId, TrackDialogState.IDLE.name());
            return "Link added to tracking: " + session.url();
        } catch (ScrapperGatewayException exception) {
            stateRepository.clear(chatId);
            botLogger.logTrackDialogState(chatId, TrackDialogState.IDLE.name());
            return SCRAPPER_ERROR_REPLIES.getOrDefault(exception.getClass(), SCRAPPER_UNAVAILABLE_REPLY);
        }
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.strip();
    }
}

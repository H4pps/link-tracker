package backend.academy.linktracker.bot.application.track;

import backend.academy.linktracker.bot.application.scrapper.AddScrapperLinkCommand;
import backend.academy.linktracker.bot.application.scrapper.ScrapperGateway;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperConflictException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperNotFoundException;
import backend.academy.linktracker.bot.application.scrapper.exception.ScrapperUnavailableException;
import backend.academy.linktracker.bot.logging.BotLogger;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Application service for chat-scoped `/track` dialog state machine.
 */
@Component
@RequiredArgsConstructor
public class TrackDialogService {

    public static final String TRACK_REPLY =
            "Введите ссылку, которую хотите отслеживать. Для отмены используйте /cancel.";
    public static final String TAGS_REPLY = "Введите теги через запятую (или отправьте пустое сообщение).";
    public static final String FILTERS_REPLY = "Введите фильтры через запятую (или отправьте пустое сообщение).";
    public static final String CANCELLED_REPLY = "Отслеживание ссылки отменено.";
    public static final String DUPLICATE_REPLY = "Ссылка уже отслеживается";
    public static final String INVALID_URL_REPLY =
            "Некорректная ссылка. Поддерживаются только GitHub репозитории и вопросы StackOverflow.";
    public static final String SCRAPPER_UNAVAILABLE_REPLY = "Сервис Scrapper временно недоступен. Попробуйте позже.";
    public static final String CHAT_NOT_REGISTERED_REPLY = "Чат не зарегистрирован. Используйте /start.";
    private static final String COMMA_SEPARATOR = ",";

    private final TrackDialogStateRepository stateRepository;
    private final ScrapperGateway scrapperGateway;
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
        if (!isSupportedTrackUrl(candidate)) {
            return INVALID_URL_REPLY;
        }

        stateRepository.save(chatId, new TrackDialogSession(TrackDialogState.AWAITING_TAGS, candidate, List.of()));
        botLogger.logTrackDialogState(chatId, TrackDialogState.AWAITING_TAGS.name());
        return TAGS_REPLY;
    }

    private String handleTags(long chatId, TrackDialogSession session, String messageText) {
        List<String> tags = parseCsv(messageText);
        stateRepository.save(chatId, new TrackDialogSession(TrackDialogState.AWAITING_FILTERS, session.url(), tags));
        botLogger.logTrackDialogState(chatId, TrackDialogState.AWAITING_FILTERS.name());
        return FILTERS_REPLY;
    }

    private String handleFilters(long chatId, TrackDialogSession session, String messageText) {
        List<String> filters = parseCsv(messageText);
        try {
            scrapperGateway.addLink(chatId, new AddScrapperLinkCommand(session.url(), session.tags(), filters));
            stateRepository.clear(chatId);
            botLogger.logTrackDialogState(chatId, TrackDialogState.IDLE.name());
            return "Ссылка добавлена в отслеживание: " + session.url();
        } catch (ScrapperConflictException exception) {
            stateRepository.clear(chatId);
            botLogger.logTrackDialogState(chatId, TrackDialogState.IDLE.name());
            return DUPLICATE_REPLY;
        } catch (ScrapperNotFoundException exception) {
            stateRepository.clear(chatId);
            botLogger.logTrackDialogState(chatId, TrackDialogState.IDLE.name());
            return CHAT_NOT_REGISTERED_REPLY;
        } catch (ScrapperUnavailableException exception) {
            stateRepository.clear(chatId);
            botLogger.logTrackDialogState(chatId, TrackDialogState.IDLE.name());
            return SCRAPPER_UNAVAILABLE_REPLY;
        }
    }

    private List<String> parseCsv(String input) {
        String normalized = normalize(input);
        if (normalized.isBlank()) {
            return List.of();
        }

        return Arrays.stream(normalized.split(COMMA_SEPARATOR))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.strip();
    }

    private boolean isSupportedTrackUrl(String candidate) {
        try {
            URI uri = URI.create(candidate);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return false;
            }

            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))) {
                return false;
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String[] segments = Arrays.stream(uri.getPath().split("/"))
                    .filter(part -> !part.isBlank())
                    .toArray(String[]::new);

            boolean github = ("github.com".equals(host) || "www.github.com".equals(host)) && segments.length >= 2;
            boolean stackoverflow = ("stackoverflow.com".equals(host) || "www.stackoverflow.com".equals(host))
                    && segments.length >= 2
                    && "questions".equals(segments[0])
                    && segments[1].chars().allMatch(Character::isDigit);
            return github || stackoverflow;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}

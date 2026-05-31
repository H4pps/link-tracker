package backend.academy.linktracker.scrapper.application.update;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Encoding helper for compact outbox payload persistence.
 */
public final class LinkUpdateOutboxPayloadCodec {

    private LinkUpdateOutboxPayloadCodec() {}

    public static String encodeChatIds(List<Long> chatIds) {
        if (chatIds == null || chatIds.isEmpty()) {
            return "";
        }
        return chatIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public static List<Long> decodeChatIds(String rawChatIds) {
        if (rawChatIds == null || rawChatIds.isBlank()) {
            return List.of();
        }
        return List.of(rawChatIds.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::parseLong)
                .toList();
    }
}

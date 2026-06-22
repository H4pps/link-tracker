package com.linktracker.scrapper.infrastructure.cache;

import com.linktracker.scrapper.application.link.LinkView;
import com.linktracker.scrapper.application.link.ListLinksCache;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Disabled list-links cache implementation.
 */
@Component
@ConditionalOnProperty(prefix = "app.cache.list-links", name = "enabled", havingValue = "false")
public class NoOpListLinksCache implements ListLinksCache {

    @Override
    public Optional<List<LinkView>> get(long chatId) {
        return Optional.empty();
    }

    @Override
    public void put(long chatId, List<LinkView> links) {}

    @Override
    public void evict(long chatId) {}
}

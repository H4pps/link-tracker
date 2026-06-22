package com.linktracker.scrapper.infrastructure.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linktracker.scrapper.application.link.LinkView;
import com.linktracker.scrapper.application.link.ListLinksCache;
import com.linktracker.scrapper.logging.ScrapperLogger;
import com.linktracker.scrapper.properties.CacheProperties;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Valkey-compatible Redis cache for unpaged list-links responses.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.cache.list-links", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisListLinksCache implements ListLinksCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;
    private final ScrapperLogger scrapperLogger;

    @Override
    public Optional<List<LinkView>> get(long chatId) {
        try {
            String payload = redisTemplate.opsForValue().get(cacheKey(chatId));
            if (payload == null) {
                return Optional.empty();
            }
            CachedListLinksResponse response = objectMapper.readValue(payload, CachedListLinksResponse.class);
            return Optional.of(response.links().stream().map(this::toLinkView).toList());
        } catch (JsonProcessingException | RuntimeException exception) {
            scrapperLogger.logCacheReadFailed(chatId, exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(long chatId, List<LinkView> links) {
        try {
            CachedListLinksResponse response = new CachedListLinksResponse(
                    links.stream().map(this::toCachedLink).toList(), links.size());
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate
                    .opsForValue()
                    .set(
                            cacheKey(chatId),
                            payload,
                            cacheProperties.getListLinks().getTtl());
        } catch (JsonProcessingException | RuntimeException exception) {
            scrapperLogger.logCacheWriteFailed(chatId, exception);
        }
    }

    @Override
    public void evict(long chatId) {
        try {
            redisTemplate.delete(cacheKey(chatId));
        } catch (RuntimeException exception) {
            scrapperLogger.logCacheEvictFailed(chatId, exception);
        }
    }

    private String cacheKey(long chatId) {
        return Long.toString(chatId);
    }

    private CachedLink toCachedLink(LinkView link) {
        return new CachedLink(link.id(), link.url(), List.copyOf(link.tags()), List.copyOf(link.filters()));
    }

    private LinkView toLinkView(CachedLink link) {
        return new LinkView(link.id(), link.url(), safeList(link.tags()), safeList(link.filters()));
    }

    private List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return List.copyOf(values);
    }

    private record CachedListLinksResponse(List<CachedLink> links, int size) {}

    private record CachedLink(long id, String url, List<String> tags, List<String> filters) {}
}

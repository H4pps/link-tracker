package backend.academy.linktracker.scrapper.infrastructure.memory.inmemory;

import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.tag.TagDeleteResult;
import backend.academy.linktracker.scrapper.application.tag.TagDeleteStatus;
import backend.academy.linktracker.scrapper.application.tag.TagRenameResult;
import backend.academy.linktracker.scrapper.application.tag.TagRenameStatus;
import backend.academy.linktracker.scrapper.application.tag.TagRepository;
import backend.academy.linktracker.scrapper.domain.model.Tag;
import backend.academy.linktracker.scrapper.domain.model.TrackedSubscription;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of standalone tag repository.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "MEMORY")
public class InMemoryTagRepository implements TagRepository {

    private final InMemoryScrapperStorage storage;

    private final ConcurrentMap<Long, Tag> tagsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> tagIdsByName = new ConcurrentHashMap<>();
    private final AtomicLong tagIdSequence = new AtomicLong();

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Optional<Tag> create(String name) {
        if (tagIdsByName.containsKey(name)) {
            return Optional.empty();
        }
        Tag tag = new Tag(tagIdSequence.incrementAndGet(), name);
        tagsById.put(tag.id(), tag);
        tagIdsByName.put(name, tag.id());
        return Optional.of(tag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Tag> findById(long id) {
        return Optional.ofNullable(tagsById.get(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Tag> findByName(String name) {
        Long tagId = tagIdsByName.get(name);
        return tagId == null ? Optional.empty() : Optional.ofNullable(tagsById.get(tagId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Tag> findAll(RepositoryPageRequest pageRequest) {
        List<Tag> sorted = tagsById.values().stream()
                .sorted(Comparator.comparingLong(Tag::id))
                .toList();
        int fromIndex = (int) Math.min(pageRequest.offset(), sorted.size());
        int toIndex = pageRequest.bounded()
                ? (int) Math.min((long) fromIndex + pageRequest.limit(), sorted.size())
                : sorted.size();
        return new ArrayList<>(sorted.subList(fromIndex, toIndex));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized TagRenameResult rename(long id, String name) {
        Tag existing = tagsById.get(id);
        if (existing == null) {
            return new TagRenameResult(TagRenameStatus.MISSING);
        }
        Long conflictId = tagIdsByName.get(name);
        if (conflictId != null && conflictId != id) {
            return new TagRenameResult(TagRenameStatus.DUPLICATE);
        }
        tagsById.put(id, new Tag(id, name));
        tagIdsByName.remove(existing.name());
        tagIdsByName.put(name, id);
        return new TagRenameResult(TagRenameStatus.RENAMED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized TagDeleteResult deleteIfUnused(long id) {
        Tag existing = tagsById.get(id);
        if (existing == null) {
            return new TagDeleteResult(TagDeleteStatus.MISSING);
        }
        if (isAttached(existing.name())) {
            return new TagDeleteResult(TagDeleteStatus.ATTACHED);
        }
        tagsById.remove(id);
        tagIdsByName.remove(existing.name());
        return new TagDeleteResult(TagDeleteStatus.DELETED);
    }

    private boolean isAttached(String tagName) {
        for (Map<String, TrackedSubscription> subscriptionsByUrl :
                storage.subscriptionsByChatSnapshot().values()) {
            for (TrackedSubscription subscription : subscriptionsByUrl.values()) {
                if (subscription.tags().contains(tagName)) {
                    return true;
                }
            }
        }
        return false;
    }
}

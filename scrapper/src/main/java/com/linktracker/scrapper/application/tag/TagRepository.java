package com.linktracker.scrapper.application.tag;

import com.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import com.linktracker.scrapper.domain.model.Tag;
import java.util.List;
import java.util.Optional;

/**
 * Repository boundary for standalone tag management.
 */
public interface TagRepository {

    /**
     * Creates a new tag.
     *
     * @param name tag name
     * @return created tag or empty optional when tag name already exists
     */
    Optional<Tag> create(String name);

    /**
     * Finds tag by id.
     *
     * @param id internal tag identifier
     * @return tag optional
     */
    Optional<Tag> findById(long id);

    /**
     * Finds tag by unique name.
     *
     * @param name tag name
     * @return tag optional
     */
    Optional<Tag> findByName(String name);

    /**
     * Lists tags in deterministic order.
     *
     * @param pageRequest page bounds, unbounded when limit is zero
     * @return page of tags
     */
    List<Tag> findAll(RepositoryPageRequest pageRequest);

    /**
     * Renames a tag.
     *
     * @param id tag identifier
     * @param name new tag name
     * @return rename result
     */
    TagRenameResult rename(long id, String name);

    /**
     * Deletes tag only when not attached to subscriptions.
     *
     * @param id tag identifier
     * @return delete result
     */
    TagDeleteResult deleteIfUnused(long id);
}

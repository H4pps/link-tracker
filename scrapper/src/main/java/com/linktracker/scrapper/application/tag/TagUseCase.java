package com.linktracker.scrapper.application.tag;

import com.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import com.linktracker.scrapper.domain.model.Tag;
import java.util.List;

/**
 * Tag management use case boundary.
 */
public interface TagUseCase {

    /**
     * Creates a standalone tag.
     *
     * @param name tag name
     * @return created tag
     */
    Tag createTag(String name);

    /**
     * Finds tag by identifier.
     *
     * @param tagId internal tag identifier
     * @return found tag
     */
    Tag getTag(long tagId);

    /**
     * Finds tag by name.
     *
     * @param name tag name
     * @return found tag
     */
    Tag getTagByName(String name);

    /**
     * Lists tags with optional bounds.
     *
     * @param pageRequest page bounds, unbounded when limit is zero
     * @return deterministic tag list
     */
    List<Tag> listTags(RepositoryPageRequest pageRequest);

    /**
     * Renames tag.
     *
     * @param tagId internal tag identifier
     * @param name new tag name
     * @return updated tag projection
     */
    Tag renameTag(long tagId, String name);

    /**
     * Deletes tag when it is not attached to subscriptions.
     *
     * @param tagId internal tag identifier
     */
    void deleteTag(long tagId);
}

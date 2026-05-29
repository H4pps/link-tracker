package backend.academy.linktracker.scrapper.infrastructure.memory.sql;

import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.tag.TagDeleteResult;
import backend.academy.linktracker.scrapper.application.tag.TagDeleteStatus;
import backend.academy.linktracker.scrapper.application.tag.TagRenameResult;
import backend.academy.linktracker.scrapper.application.tag.TagRenameStatus;
import backend.academy.linktracker.scrapper.application.tag.TagRepository;
import backend.academy.linktracker.scrapper.domain.model.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * SQL implementation of standalone tag repository.
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.database.access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlTagRepository implements TagRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Optional<Tag> create(String name) {
        int inserted = jdbcTemplate.update("INSERT INTO tags (name) VALUES (?) ON CONFLICT (name) DO NOTHING", name);
        if (inserted == 0) {
            return Optional.empty();
        }
        Long id = jdbcTemplate.queryForObject("SELECT id FROM tags WHERE name = ?", Long.class, name);
        return id == null ? Optional.empty() : Optional.of(new Tag(id, name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Tag> findById(long id) {
        return jdbcTemplate
                .query(
                        "SELECT id, name FROM tags WHERE id = ?",
                        (resultSet, rowNum) -> new Tag(resultSet.getLong("id"), resultSet.getString("name")),
                        id)
                .stream()
                .findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Tag> findByName(String name) {
        return jdbcTemplate
                .query(
                        "SELECT id, name FROM tags WHERE name = ?",
                        (resultSet, rowNum) -> new Tag(resultSet.getLong("id"), resultSet.getString("name")),
                        name)
                .stream()
                .findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<Tag> findAll(RepositoryPageRequest pageRequest) {
        String sql = applyPagination(
                """
                SELECT id, name
                FROM tags
                ORDER BY id
                """,
                pageRequest);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new Tag(resultSet.getLong("id"), resultSet.getString("name")),
                paginationArguments(pageRequest).toArray());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TagRenameResult rename(long id, String name) {
        int updated = jdbcTemplate.update(
                """
                UPDATE tags
                SET name = ?
                WHERE id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM tags duplicate
                      WHERE duplicate.name = ?
                        AND duplicate.id <> ?
                  )
                """,
                name,
                id,
                name,
                id);
        if (updated > 0) {
            return new TagRenameResult(TagRenameStatus.RENAMED);
        }
        return findById(id).isPresent()
                ? new TagRenameResult(TagRenameStatus.DUPLICATE)
                : new TagRenameResult(TagRenameStatus.MISSING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TagDeleteResult deleteIfUnused(long id) {
        int deleted = jdbcTemplate.update(
                """
                DELETE FROM tags
                WHERE id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM subscription_tags
                      WHERE tag_id = ?
                  )
                """,
                id,
                id);
        if (deleted > 0) {
            return new TagDeleteResult(TagDeleteStatus.DELETED);
        }
        return findById(id).isPresent()
                ? new TagDeleteResult(TagDeleteStatus.ATTACHED)
                : new TagDeleteResult(TagDeleteStatus.MISSING);
    }

    private String applyPagination(String sql, RepositoryPageRequest pageRequest) {
        StringBuilder builder = new StringBuilder(sql);
        if (pageRequest.bounded()) {
            builder.append("\nLIMIT ?");
        }
        if (pageRequest.offset() > 0) {
            builder.append("\nOFFSET ?");
        }
        return builder.toString();
    }

    private List<Object> paginationArguments(RepositoryPageRequest pageRequest) {
        List<Object> arguments = new ArrayList<>();
        if (pageRequest.bounded()) {
            arguments.add(pageRequest.limit());
        }
        if (pageRequest.offset() > 0) {
            arguments.add(pageRequest.offset());
        }
        return arguments;
    }
}

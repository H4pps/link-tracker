package backend.academy.linktracker.scrapper.application.tag;

import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.ConflictException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import backend.academy.linktracker.scrapper.domain.model.Tag;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Repository-backed implementation of standalone tag management.
 */
@Component
@RequiredArgsConstructor
public class TagUseCaseImpl implements TagUseCase {

    private final TagRepository tagRepository;
    private final ScrapperLogger scrapperLogger;

    /**
     * {@inheritDoc}
     */
    @Override
    public Tag createTag(String name) {
        validateName(name);
        scrapperLogger.logUseCaseAccepted("create-tag", null, name);
        return tagRepository.create(name).orElseThrow(() -> new AlreadyExistsException("Tag already exists: " + name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tag getTag(long tagId) {
        scrapperLogger.logUseCaseAccepted("get-tag", null, String.valueOf(tagId));
        return tagRepository.findById(tagId).orElseThrow(() -> new NotFoundException("Tag not found: " + tagId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tag getTagByName(String name) {
        validateName(name);
        scrapperLogger.logUseCaseAccepted("get-tag-by-name", null, name);
        return tagRepository.findByName(name).orElseThrow(() -> new NotFoundException("Tag not found: " + name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Tag> listTags(RepositoryPageRequest pageRequest) {
        scrapperLogger.logUseCaseAccepted("list-tags", null, null);
        return tagRepository.findAll(pageRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tag renameTag(long tagId, String name) {
        validateName(name);
        scrapperLogger.logUseCaseAccepted("rename-tag", null, name);
        TagRenameResult result = tagRepository.rename(tagId, name);
        if (result.status() == TagRenameStatus.MISSING) {
            throw new NotFoundException("Tag not found: " + tagId);
        }
        if (result.status() == TagRenameStatus.DUPLICATE) {
            throw new AlreadyExistsException("Tag already exists: " + name);
        }
        return new Tag(tagId, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteTag(long tagId) {
        scrapperLogger.logUseCaseAccepted("delete-tag", null, String.valueOf(tagId));
        TagDeleteResult result = tagRepository.deleteIfUnused(tagId);
        if (result.status() == TagDeleteStatus.MISSING) {
            throw new NotFoundException("Tag not found: " + tagId);
        }
        if (result.status() == TagDeleteStatus.ATTACHED) {
            throw new ConflictException("Tag is attached to at least one subscription: " + tagId);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tag name must not be blank");
        }
    }
}

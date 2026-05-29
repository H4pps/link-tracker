package backend.academy.linktracker.scrapper.application.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.application.pagination.RepositoryPageRequest;
import backend.academy.linktracker.scrapper.application.tag.TagDeleteResult;
import backend.academy.linktracker.scrapper.application.tag.TagDeleteStatus;
import backend.academy.linktracker.scrapper.application.tag.TagRenameResult;
import backend.academy.linktracker.scrapper.application.tag.TagRenameStatus;
import backend.academy.linktracker.scrapper.application.tag.TagRepository;
import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.ConflictException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import backend.academy.linktracker.scrapper.domain.model.Tag;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagUseCaseImplTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ScrapperLogger scrapperLogger;

    private TagUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TagUseCaseImpl(tagRepository, scrapperLogger);
    }

    @Test
    void createTagThrowsAlreadyExistsForDuplicateName() {
        when(tagRepository.create("team")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.createTag("team"))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Tag already exists: team");
    }

    @Test
    void createAndRenameRejectBlankNames() {
        assertThatThrownBy(() -> useCase.createTag(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tag name must not be blank");

        assertThatThrownBy(() -> useCase.renameTag(10L, "\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tag name must not be blank");
    }

    @Test
    void getTagAndGetTagByNameThrowNotFoundWhenMissing() {
        when(tagRepository.findById(11L)).thenReturn(Optional.empty());
        when(tagRepository.findByName("ops")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getTag(11L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Tag not found: 11");
        assertThatThrownBy(() -> useCase.getTagByName("ops"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Tag not found: ops");
    }

    @Test
    void renameTagMapsDuplicateAndMissingToDomainExceptions() {
        when(tagRepository.rename(11L, "ops")).thenReturn(new TagRenameResult(TagRenameStatus.DUPLICATE));
        when(tagRepository.rename(12L, "ops")).thenReturn(new TagRenameResult(TagRenameStatus.MISSING));

        assertThatThrownBy(() -> useCase.renameTag(11L, "ops"))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Tag already exists: ops");
        assertThatThrownBy(() -> useCase.renameTag(12L, "ops"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Tag not found: 12");
    }

    @Test
    void deleteTagMapsMissingAndAttachedCases() {
        when(tagRepository.deleteIfUnused(22L)).thenReturn(new TagDeleteResult(TagDeleteStatus.MISSING));
        when(tagRepository.deleteIfUnused(23L)).thenReturn(new TagDeleteResult(TagDeleteStatus.ATTACHED));

        assertThatThrownBy(() -> useCase.deleteTag(22L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Tag not found: 22");
        assertThatThrownBy(() -> useCase.deleteTag(23L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tag is attached to at least one subscription: 23");
    }

    @Test
    void listTagsReturnsRepositoryResult() {
        List<Tag> expected = List.of(new Tag(1L, "a"), new Tag(2L, "b"));
        when(tagRepository.findAll(new RepositoryPageRequest(2, 0))).thenReturn(expected);

        assertThat(useCase.listTags(new RepositoryPageRequest(2, 0))).isEqualTo(expected);
    }
}

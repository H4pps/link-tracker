package backend.academy.linktracker.scrapper.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class SchedulerPropertiesTest {

    @Test
    void defaultLinkPageSizeIsWithinTaskRange() {
        SchedulerProperties properties = new SchedulerProperties();

        assertThat(properties.getLinkPageSize()).isBetween(50, 500);
    }

    @Test
    void validatesLinkPageSizeTaskRange() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            SchedulerProperties tooSmall = new SchedulerProperties();
            tooSmall.setLinkPageSize(49);
            SchedulerProperties tooLarge = new SchedulerProperties();
            tooLarge.setLinkPageSize(501);

            assertThat(validator.validate(tooSmall)).isNotEmpty();
            assertThat(validator.validate(tooLarge)).isNotEmpty();
        }
    }

    @Test
    void validatesPositiveWorkerCount() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            SchedulerProperties properties = new SchedulerProperties();
            properties.setWorkerCount(0);

            assertThat(validator.validate(properties)).isNotEmpty();
        }
    }
}

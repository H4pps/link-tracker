package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties for scheduled link updates checks.
 */
@ConfigurationProperties(prefix = "app.scheduler")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class SchedulerProperties {

    private boolean enabled = true;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration interval = Duration.ofSeconds(30);

    @Min(50)
    @Max(500)
    private int linkPageSize = 100;

    @Min(1)
    private int workerCount = 1;
}

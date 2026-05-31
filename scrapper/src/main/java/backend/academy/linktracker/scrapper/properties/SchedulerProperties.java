package backend.academy.linktracker.scrapper.properties;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

/**
 * Typed properties for scheduled link updates checks.
 */
@ConfigurationProperties(prefix = "app.scheduler")
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class SchedulerProperties {

    private boolean enabled = true;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration interval = Duration.ofSeconds(30);

    private int linkPageSize = 100;

    private int workerCount = 1;
}

package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties for GitHub API client.
 */
@ConfigurationProperties(prefix = "app.github")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class GithubProperties {

    @NotEmpty
    @URL
    private String baseUrl = "https://api.github.com";

    @NotEmpty
    private String token;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration connectTimeout = Duration.ofSeconds(2);

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration readTimeout = Duration.ofSeconds(5);
}

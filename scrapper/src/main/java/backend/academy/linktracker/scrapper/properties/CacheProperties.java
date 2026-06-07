package backend.academy.linktracker.scrapper.properties;

import java.time.Duration;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed properties for Scrapper cache settings.
 */
@ConfigurationProperties(prefix = "app.cache")
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class CacheProperties {

    private boolean clientSideEnabled = true;
    private ListLinks listLinks = new ListLinks();

    /**
     * List-links cache settings.
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class ListLinks {

        private boolean enabled = true;
        private Duration ttl = Duration.ofMinutes(10);
    }
}

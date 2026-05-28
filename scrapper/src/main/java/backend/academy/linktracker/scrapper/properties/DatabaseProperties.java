package backend.academy.linktracker.scrapper.properties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed properties for scrapper persistence access strategy.
 */
@ConfigurationProperties(prefix = "app.database")
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class DatabaseProperties {

    private AccessType accessType = AccessType.SQL;

    /**
     * Persistence access strategy.
     */
    public enum AccessType {
        SQL,
        ORM
    }
}

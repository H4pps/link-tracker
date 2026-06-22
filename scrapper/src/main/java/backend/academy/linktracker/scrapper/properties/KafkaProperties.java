package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.Min;
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
 * Typed properties for Kafka transport and outbox publishing.
 */
@ConfigurationProperties(prefix = "app.kafka")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class KafkaProperties {

    @NotEmpty
    private String bootstrapServers = "localhost:9092";

    @NotEmpty
    @URL
    private String schemaRegistryUrl = "http://localhost:8085";

    @NotEmpty
    private String rawUpdatesTopic = "link.raw-updates";

    @Min(1)
    private int maxAttempts = 3;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration retryBackoff = Duration.ofSeconds(1);

    @Min(1)
    private int outboxBatchSize = 100;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration outboxPublishInterval = Duration.ofSeconds(5);
}

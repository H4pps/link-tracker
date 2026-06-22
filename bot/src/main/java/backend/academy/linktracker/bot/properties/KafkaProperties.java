package backend.academy.linktracker.bot.properties;

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
 * Typed properties for Kafka consumer and DLQ behavior.
 */
@ConfigurationProperties(prefix = "app.kafka")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class KafkaProperties {

    private boolean enabled = true;

    @NotEmpty
    private String bootstrapServers = "localhost:9092";

    @NotEmpty
    @URL
    private String schemaRegistryUrl = "http://localhost:8085";

    @NotEmpty
    private String processedUpdatesTopic = "link.processed-updates";

    @NotEmpty
    private String processedUpdatesDlqTopic = "link.processed-updates-dlq";

    @NotEmpty
    private String consumerGroup = "link-tracker-bot";

    @Min(1)
    private int maxAttempts = 3;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration retryBackoff = Duration.ofSeconds(1);
}

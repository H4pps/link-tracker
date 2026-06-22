package com.linktracker.scrapper.properties;

import jakarta.validation.constraints.Max;
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
 * Typed properties for scrapper-to-bot HTTP notifications.
 */
@ConfigurationProperties(prefix = "app.bot")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class BotProperties {

    private TransportMode mode = TransportMode.KAFKA;

    @NotEmpty
    @URL
    private String baseUrl = "http://localhost:8080";

    @NotEmpty
    private String grpcHost = "localhost";

    @Min(1)
    @Max(65535)
    private int grpcPort = 9090;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration grpcDeadline = Duration.ofSeconds(3);

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration connectTimeout = Duration.ofSeconds(2);

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration readTimeout = Duration.ofSeconds(5);

    /**
     * Transport mode for scrapper to bot calls.
     */
    public enum TransportMode {
        KAFKA,
        HTTP,
        GRPC
    }
}

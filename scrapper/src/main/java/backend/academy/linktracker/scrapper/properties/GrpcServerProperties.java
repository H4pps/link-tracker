package backend.academy.linktracker.scrapper.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties for embedded gRPC server in scrapper service.
 */
@ConfigurationProperties(prefix = "app.grpc.server")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class GrpcServerProperties {

    @Min(0)
    @Max(65535)
    private int port = 9091;
}

package backend.academy.linktracker.scrapper.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BotPropertiesTransportModeContractTest {

    @Test
    void defaultTransportModeIsKafka() {
        BotProperties properties = new BotProperties();

        assertThat(properties.getMode().name()).isEqualTo("KAFKA");
    }

    @Test
    void transportModeContainsKafkaGrpcAndHttp() {
        Set<String> transportModes = Arrays.stream(BotProperties.TransportMode.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(transportModes).containsExactlyInAnyOrder("KAFKA", "GRPC", "HTTP");
        assertThat(BotProperties.TransportMode.valueOf("KAFKA").name()).isEqualTo("KAFKA");
    }
}

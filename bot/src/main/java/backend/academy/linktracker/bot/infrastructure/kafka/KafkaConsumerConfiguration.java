package backend.academy.linktracker.bot.infrastructure.kafka;

import backend.academy.linktracker.bot.properties.KafkaProperties;
import backend.academy.linktracker.messaging.LinkUpdateEvent;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfiguration {

    @Bean(destroyMethod = "close")
    KafkaAvroDeserializer kafkaAvroDeserializer(KafkaProperties kafkaProperties) {
        KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer();
        deserializer.configure(
                Map.of("schema.registry.url", kafkaProperties.getSchemaRegistryUrl(), "specific.avro.reader", true),
                false);
        return deserializer;
    }

    @Bean
    ConsumerFactory<String, byte[]> rawKafkaConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumerGroup());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaRawListenerContainerFactory(
            ConsumerFactory<String, byte[]> rawKafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(rawKafkaConsumerFactory);
        return factory;
    }

    @Bean
    ProducerFactory<String, byte[]> kafkaBytesProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, byte[]> kafkaBytesTemplate(ProducerFactory<String, byte[]> kafkaBytesProducerFactory) {
        return new KafkaTemplate<>(kafkaBytesProducerFactory);
    }

    @Bean
    ProducerFactory<String, LinkUpdateEvent> kafkaAvroProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, kafkaProperties.getSchemaRegistryUrl());
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, LinkUpdateEvent> kafkaTemplate(
            ProducerFactory<String, LinkUpdateEvent> kafkaAvroProducerFactory) {
        return new KafkaTemplate<>(kafkaAvroProducerFactory);
    }
}

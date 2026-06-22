package com.linktracker.ai.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.linktracker.ai.properties.AiAgentProperties.SummarizationMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AiAgentPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsDefaultValues() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiAgentProperties.class);

            AiAgentProperties properties = context.getBean(AiAgentProperties.class);

            assertThat(properties.getFiltering().getStopWords()).isEmpty();
            assertThat(properties.getFiltering().getExcludedAuthors()).isEmpty();
            assertThat(properties.getFiltering().getMinLength()).isEqualTo(20);
            assertThat(properties.getSummarization().getMode()).isEqualTo(SummarizationMode.STUB);
            assertThat(properties.getSummarization().getThreshold()).isEqualTo(500);
        });
    }

    @Test
    void bindsOverrideValues() {
        contextRunner
                .withPropertyValues(
                        "ai-agent.filtering.stop-words=spam,ads,promo",
                        "ai-agent.filtering.excluded-authors=bot-user",
                        "ai-agent.filtering.min-length=30",
                        "ai-agent.summarization.mode=ai",
                        "ai-agent.summarization.threshold=120")
                .run(context -> {
                    AiAgentProperties properties = context.getBean(AiAgentProperties.class);

                    assertThat(properties.getFiltering().getStopWords()).containsExactly("spam", "ads", "promo");
                    assertThat(properties.getFiltering().getExcludedAuthors()).containsExactly("bot-user");
                    assertThat(properties.getFiltering().getMinLength()).isEqualTo(30);
                    assertThat(properties.getSummarization().getMode()).isEqualTo(SummarizationMode.AI);
                    assertThat(properties.getSummarization().getThreshold()).isEqualTo(120);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiAgentProperties.class)
    static class TestConfiguration {}
}

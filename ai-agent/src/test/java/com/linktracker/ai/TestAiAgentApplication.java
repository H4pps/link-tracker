package com.linktracker.ai;

import org.springframework.boot.SpringApplication;

public class TestAiAgentApplication {

    static void main(String[] args) {
        SpringApplication.from(AiAgentApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}

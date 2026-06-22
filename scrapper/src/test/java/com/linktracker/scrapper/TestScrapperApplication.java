package com.linktracker.scrapper;

import org.springframework.boot.SpringApplication;

public class TestScrapperApplication {

    static void main(String[] args) {
        SpringApplication.from(ScrapperApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}

package com.linktracker.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Boot application entry point for the Telegram bot service.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BotApplication {

    /**
     * Starts the Spring application context.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BotApplication.class, args);
    }
}

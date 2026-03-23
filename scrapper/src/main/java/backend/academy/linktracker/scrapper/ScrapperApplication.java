package backend.academy.linktracker.scrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Boot entry point for scrapper service.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ScrapperApplication {

    /**
     * Starts the Spring application context.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ScrapperApplication.class, args);
    }
}

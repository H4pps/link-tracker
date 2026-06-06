package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SchedulerWorkerConfiguration {

    @Bean(destroyMethod = "shutdown")
    ExecutorService schedulerWorkerExecutor(SchedulerProperties schedulerProperties) {
        return Executors.newFixedThreadPool(schedulerProperties.getWorkerCount());
    }
}

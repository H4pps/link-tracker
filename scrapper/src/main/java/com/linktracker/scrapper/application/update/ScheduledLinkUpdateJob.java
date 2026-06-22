package com.linktracker.scrapper.application.update;

import com.linktracker.scrapper.properties.SchedulerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for link updates checks.
 */
@Component
@RequiredArgsConstructor
public class ScheduledLinkUpdateJob {

    private final SchedulerProperties schedulerProperties;
    private final LinkUpdateSchedulerUseCase schedulerUseCase;

    /**
     * Runs update-check batch on fixed delay.
     */
    @Scheduled(fixedDelayString = "${app.scheduler.interval:30s}")
    public void run() {
        if (!schedulerProperties.isEnabled()) {
            return;
        }
        schedulerUseCase.checkUpdates();
    }
}

package backend.academy.linktracker.bot.api.rest.config;

import backend.academy.linktracker.bot.api.rest.interceptors.BotApiLoggingInterceptor;
import backend.academy.linktracker.bot.api.rest.ratelimit.IpRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers bot REST API interceptors.
 */
@Configuration
@RequiredArgsConstructor
public class BotApiWebMvcConfigurer implements WebMvcConfigurer {

    private final BotApiLoggingInterceptor loggingInterceptor;
    private final IpRateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor).addPathPatterns("/updates");
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/updates");
    }
}

package backend.academy.linktracker.scrapper.api.rest.config;

import backend.academy.linktracker.scrapper.api.rest.interceptors.ScrapperApiLoggingInterceptor;
import backend.academy.linktracker.scrapper.api.rest.ratelimit.IpRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers scrapper REST API interceptors.
 */
@Configuration
@RequiredArgsConstructor
public class ScrapperApiWebMvcConfigurer implements WebMvcConfigurer {

    private final ScrapperApiLoggingInterceptor loggingInterceptor;
    private final IpRateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor).addPathPatterns("/links", "/tg-chat/**");
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/links", "/tg-chat/**");
    }
}

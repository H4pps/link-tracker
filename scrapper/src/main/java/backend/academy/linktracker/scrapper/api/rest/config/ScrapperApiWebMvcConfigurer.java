package backend.academy.linktracker.scrapper.api.rest.config;

import backend.academy.linktracker.scrapper.api.rest.interceptors.ScrapperApiLoggingInterceptor;
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor).addPathPatterns("/links", "/tg-chat/**");
    }
}

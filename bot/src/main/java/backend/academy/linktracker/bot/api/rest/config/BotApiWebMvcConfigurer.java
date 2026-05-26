package backend.academy.linktracker.bot.api.rest.config;

import backend.academy.linktracker.bot.api.rest.interceptors.BotApiLoggingInterceptor;
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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor).addPathPatterns("/updates");
    }
}

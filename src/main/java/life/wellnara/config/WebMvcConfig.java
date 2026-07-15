package life.wellnara.config;

import life.wellnara.service.SessionUserService;
import life.wellnara.web.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Registers custom Spring MVC argument resolvers.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SessionUserService sessionUserService;

    public WebMvcConfig(SessionUserService sessionUserService) {
        this.sessionUserService = sessionUserService;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver(sessionUserService));
    }
}

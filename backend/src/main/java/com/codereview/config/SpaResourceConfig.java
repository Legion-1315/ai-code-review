package com.codereview.config;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the bundled React SPA from {@code classpath:/static/}.
 * <p>
 * Behaviour:
 * <ul>
 *   <li>If the requested path corresponds to a real static resource (e.g.
 *       {@code /assets/index-abc.js}, {@code /favicon.ico}), it is served with
 *       its natural MIME type by the framework.</li>
 *   <li>Otherwise the resolver falls back to {@code index.html} so that
 *       React Router owns client-side routes like {@code /login} or
 *       {@code /reviews/123}.</li>
 * </ul>
 * Spring MVC gives {@code @RestController} mappings higher priority than
 * resource handlers, so {@code /api/**}, {@code /actuator/**}, etc. are still
 * routed to their controllers first.
 */
@Component
public class SpaResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Client-side route → let React Router handle it
                        return location.createRelative("index.html");
                    }
                });
    }
}

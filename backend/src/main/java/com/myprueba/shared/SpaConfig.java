package com.myprueba.shared;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built frontend from {@code classpath:/static/} on the same origin as the API, so the
 * {@code SameSite=Strict} session cookie just works. A path that is not a file and has no extension
 * is a client route and gets {@code index.html}; API paths and missing files never do.
 */
@Configuration(proxyBeanMethods = false)
class SpaConfig implements WebMvcConfigurer {

    private static final Resource SHELL = new ClassPathResource("static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = super.getResource(path, location);
                        if (file != null) return file;
                        boolean clientRoute = !path.startsWith("api/") && !path.contains(".");
                        return clientRoute && SHELL.exists() ? SHELL : null;
                    }
                });
    }
}

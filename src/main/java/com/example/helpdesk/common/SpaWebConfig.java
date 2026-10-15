package com.example.helpdesk.common;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

// Serves the React app, which the Docker build places in classpath:/static/.
// Real files (index.html, /assets/*.js, ...) are returned as-is. Any other path that
// isn't an API call, e.g. /tickets/5 after a browser reload, gets index.html so React
// Router can show the right page; those URLs exist only in the browser, not on disk.
// Controllers are matched before this handler, so the REST API is unaffected.
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

	private static final Resource INDEX_HTML = new ClassPathResource("/static/index.html");

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
			.addResourceLocations("classpath:/static/")
			.resourceChain(true)
			.addResolver(new PathResourceResolver() {
				@Override
				protected Resource getResource(String path, Resource location) throws IOException {
					Resource file = location.createRelative(path);
					if (file.exists() && file.isReadable()) {
						return file;
					}
					// Unknown API paths stay 404s; and without a built UI (plain local
					// development) there's no index.html to fall back to.
					if (path.startsWith("api/") || !INDEX_HTML.exists()) {
						return null;
					}
					return INDEX_HTML;
				}
			});
	}

}

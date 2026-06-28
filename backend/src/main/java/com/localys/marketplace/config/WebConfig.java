package com.localys.marketplace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.media.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!uploadPath.toString().startsWith("/")) {
                uploadPath = Paths.get("/workspace/uploads").toAbsolutePath().normalize();
            }
            String location = "file:" + uploadPath.toString();
            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations(location + "/");
            registry.addResourceHandler("/api/uploads/**")
                    .addResourceLocations(location + "/");
        } catch (Exception e) {
            System.err.println("Error configuring upload path: " + e.getMessage());
        }
    }
}

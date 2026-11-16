package com.sharedrop.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    public OpenAPI dropShareOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DropShare API")
                        .description("Secure file and text sharing platform — no accounts required")
                        .version("1.0.0")
                        .license(new License().name("MIT")));
    }
}

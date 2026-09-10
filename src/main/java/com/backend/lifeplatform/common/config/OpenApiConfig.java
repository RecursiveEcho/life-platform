package com.backend.lifeplatform.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 接口文档基础信息配置。 */
@Configuration
public class OpenApiConfig {

    /**
     * 构建 OpenAPI 文档基础信息：声明统一的 Bearer JWT 鉴权方式，
     * 使 Swagger UI / Knife4j 上可以填写 token 后再调受保护的接口。
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .info(new Info()
                        .title("Life Platform API")
                        .version("v1")
                        .description("本地生活平台接口文档"));
    }
}

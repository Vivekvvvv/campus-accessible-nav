package com.demo.accessiblenav.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * OpenAPI/Swagger配置
 * 访问地址: /swagger-ui.html 或 /swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:accessible-nav}")
    private String applicationName;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("无障碍校园导航系统 API")
                        .version("1.0.0")
                        .description("为视障人士提供安全、便捷的校园导航服务\n\n" +
                                "## 功能模块\n" +
                                "- **认证模块**: 用户注册、登录、Token刷新\n" +
                                "- **路线规划**: 起终点路线计算、无障碍路线优化\n" +
                                "- **设施查询**: 无障碍设施信息查询、附近设施搜索\n" +
                                "- **障碍上报**: 路障信息上报与管理\n\n" +
                                "## 认证方式\n" +
                                "使用Bearer Token认证，在请求头添加:\n" +
                                "```\n" +
                                "Authorization: Bearer <your-token>\n" +
                                "```")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("support@example.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(Arrays.asList(
                        new Server().url("/").description("当前服务器"),
                        new Server().url("http://localhost:8080").description("本地开发环境")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT认证，使用登录接口获取token")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}

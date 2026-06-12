package iuh.fit.graduationthesis.common.configs;

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

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI openAPI() {
        // 1. Định nghĩa Security Scheme — Bearer JWT
        SecurityScheme bearerScheme = new SecurityScheme()
                .name("Bearer Authentication")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Nhập Access Token (không cần tiền tố 'Bearer')");

        // 2. Áp dụng Security mặc định cho tất cả API
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("Bearer Authentication");

        return new OpenAPI()
                .info(new Info()
                        .title("Graduation Thesis API")
                        .description("""
                                ## 🎓 Hệ thống API cho đồ án tốt nghiệp
                                
                                ### Tính năng chính:
                                - 🔐 **Authentication**: Đăng ký, đăng nhập (JWT RSA256)
                                - 🌐 **i18n**: Hỗ trợ đa ngôn ngữ (English / Tiếng Việt)
                                - 🛡️ **Security**: OAuth2 Resource Server + IP Validation
                                
                                ### Cách sử dụng:
                                1. Gọi **POST /api/v1/auth/register** để tạo tài khoản
                                2. Gọi **POST /api/v1/auth/login** để nhận Access Token
                                3. Nhấn nút **Authorize** 🔒 phía trên, dán Access Token vào
                                4. Gọi các API protected bình thường
                                
                                ### Đổi ngôn ngữ:
                                Thêm header `Accept-Language: vi` hoặc `Accept-Language: en`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("IUH FIT")
                                .url("https://fit.iuh.edu.vn"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development")))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", bearerScheme))
                .addSecurityItem(securityRequirement);
    }
}

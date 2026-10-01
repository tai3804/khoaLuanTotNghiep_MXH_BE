package iuh.fit.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"iuh.fit.commonframework", "iuh.fit.aiservice"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "iuh.fit.aiservice.infrastructure.feign")
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }

}

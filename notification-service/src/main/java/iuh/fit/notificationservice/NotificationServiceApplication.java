package iuh.fit.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"iuh.fit.commonframework", "iuh.fit.notificationservice"})
@EntityScan (basePackages = {"iuh.fit.notificationservice.domain.entities"})
@EnableJpaRepositories(basePackages = {"iuh.fit.notificationservice.domain.repositories"})
@EnableDiscoveryClient
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

}

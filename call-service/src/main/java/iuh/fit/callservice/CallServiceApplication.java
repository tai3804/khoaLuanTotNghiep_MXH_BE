package iuh.fit.callservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"iuh.fit.commonframework", "iuh.fit.callservice"})
@EnableDiscoveryClient
public class CallServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallServiceApplication.class, args);
    }

}

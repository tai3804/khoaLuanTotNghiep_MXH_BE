package iuh.fit.callservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Bỏ qua contextLoads khi không có môi trường Kafka/Eureka/DB thực tế")
@SpringBootTest
class CallServiceApplicationTests {
    @Test
    void contextLoads() {
    }
}

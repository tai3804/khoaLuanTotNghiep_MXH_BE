package iuh.fit.feedservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Bỏ qua contextLoads khi không có môi trường Kafka/Eureka/DB thực tế")
@SpringBootTest
class FeedServiceApplicationTests {
    @Test
    void contextLoads() {
    }
}

package iuh.fit.graduationthesis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,      // 💡 Tắt tự động cấu hình kết nối DB mặc định
        HibernateJpaAutoConfiguration.class     // 💡 Tắt tự động cấu hình JPA mặc định
})
public class GraduationThesisApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraduationThesisApplication.class, args);
    }

}

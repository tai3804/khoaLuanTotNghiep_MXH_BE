package iuh.fit.graduationthesis.common.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.List;
import java.util.Locale;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    @Value("${app.locale.default:en}")
    private String defaultLocale;

    /**
     * Xác định ngôn ngữ dựa trên Header "Accept-Language".
     * - Mặc định: đọc từ app.locale.default trong application.yaml (mặc định: en)
     * - Hỗ trợ: English (en), Vietnamese (vi)
     *
     * Cách dùng: Client gửi header "Accept-Language: vi" để nhận phản hồi tiếng Việt.
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.forLanguageTag(defaultLocale));
        resolver.setSupportedLocales(List.of(
                Locale.ENGLISH,              // en
                Locale.forLanguageTag("vi")  // vi
        ));
        return resolver;
    }

    /**
     * Cho phép đổi ngôn ngữ qua query parameter: ?lang=vi hoặc ?lang=en
     * Ví dụ: GET /api/v1/auth/login?lang=vi
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    /**
     * Kết nối Bean Validation (Hibernate Validator) với Spring MessageSource.
     * Nhờ bean này, @NotBlank(message = "{validation.username.not_blank}")
     * sẽ tự động resolve key từ file i18n/messages_xx.properties theo locale.
     */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }

    // MessageSource được Spring Boot tự động cấu hình từ spring.messages.* trong application.yaml
}


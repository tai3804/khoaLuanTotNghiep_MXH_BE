package iuh.fit.chatservice.presentation.mapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatPresentationMapperConfig {

    @Bean
    public ChatPresentationMapper chatPresentationMapper() {
        return new ChatPresentationMapperImpl();
    }
}

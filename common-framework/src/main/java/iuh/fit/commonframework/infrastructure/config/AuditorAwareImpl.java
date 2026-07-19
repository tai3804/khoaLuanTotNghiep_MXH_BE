package iuh.fit.commonframework.infrastructure.config;

import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditorAwareImpl implements AuditorAware<String> {

    JwtUtil jwtUtil;

    @Override
    public Optional<String> getCurrentAuditor() {
        String userId = jwtUtil.getCurrentUserId();
        return userId != null ? Optional.of(userId) : Optional.empty();
    }
}

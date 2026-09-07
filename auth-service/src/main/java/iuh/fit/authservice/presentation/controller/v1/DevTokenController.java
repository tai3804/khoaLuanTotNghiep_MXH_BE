package iuh.fit.authservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.authservice.presentation.dto.response.DevTokenResponse;
import iuh.fit.commonframework.application.dto.ApiResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/dev-token")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Dev Tools", description = "Development utility APIs for token generation")
public class DevTokenController {

    JwtEncoder jwtEncoder;

    @GetMapping
    @Operation(summary = "Generate permanent dev access token", description = "Generates a signed RS256 JWT access token valid until year 2050 for testing purposes")
    public ResponseEntity<ApiResponse<DevTokenResponse>> generateDevToken(
            @RequestParam(required = false) UUID userId
    ) {
        UUID targetUserId = userId != null ? userId : UUID.fromString("01912a78-4b92-7f2a-8b1c-3d4e5f6a7b8c");
        Instant farFuture = Instant.ofEpochSecond(2524608000L); // Jan 1, 2050

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://auth.iuh.fit")
                .subject(targetUserId.toString())
                .issuedAt(Instant.now())
                .expiresAt(farFuture)
                .claim("tokenVersion", 1)
                .claim("roles", List.of("ROLE_USER", "ROLE_ADMIN"))
                .build();

        JwsHeader jwsHeader = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();

        DevTokenResponse devTokenResponse = DevTokenResponse.builder()
                .accessToken(token)
                .userId(targetUserId)
                .expiresAt(farFuture)
                .build();

        return ResponseEntity.ok(ApiResponse.success(devTokenResponse, "Generated permanent dev token successfully"));
    }
}

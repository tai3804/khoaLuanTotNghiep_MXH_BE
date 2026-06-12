package iuh.fit.graduationthesis.auth.services;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import iuh.fit.graduationthesis.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtService {
    final JwtUtil jwtUtil;

    public String generateToken(String userId, Set<String> roles, Set<String> permissions, String ip) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    .build();

            Instant now = Instant.now();

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .claim("roles", roles)
                    .claim("permissions", permissions)
                    .claim("ip", ip)
                    .issuer("iuh.fit.graduationthesis")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(jwtUtil.getExpirationMinute(), ChronoUnit.MINUTES)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claimsSet);

            JWSSigner signer = new RSASSASigner(jwtUtil.getPrivateKey());

            signedJWT.sign(signer);

            return signedJWT.serialize();

        } catch (JOSEException e) {
            log.error("[JwtService]: Lỗi khi ký sinh mã Token cho user: {}", userId, e);
            throw new RuntimeException("Tạo mã token thất bại", e);
        }
    }
}
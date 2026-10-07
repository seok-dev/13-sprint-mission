package com.sprint.mission.discodeit.security.jwt;

import com.sprint.mission.discodeit.config.JwtProperties;
import com.sprint.mission.discodeit.dto.user.UserDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;

        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // AccessToken 발급
    public String generateAccessToken(UserDto userDto) {
        Instant now = Instant.now();
        Instant expiration = now.plus(jwtProperties.getAccessTokenExpiration());

        return Jwts.builder()
                .subject(userDto.id().toString())
                .id(UUID.randomUUID().toString())
                .claim("role", userDto.role().name())
                .claim("tokenType", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // RefreshToken 발급
    public String generateRefreshToken(UserDto userDto) {
        Instant now = Instant.now();
        Instant expiration = now.plus(jwtProperties.getRefreshTokenExpiration());

        return Jwts.builder()
                .subject(userDto.id().toString())
                .id(UUID.randomUUID().toString())
                .claim("tokenType", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, "ACCESS");
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, "REFRESH");
    }

    private boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);

            return claims.getExpiration() != null
                    && expectedType.equals(claims.get("tokenType", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID getUserId(String token) {
        Claims claims = parseClaims(token);
        return UUID.fromString(claims.getSubject());
    }
}

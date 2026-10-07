package com.sprint.mission.discodeit.security.jwt;

import com.sprint.mission.discodeit.config.JwtProperties;
import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtProperties properties;
    private  JwtTokenProvider provider;
    private UserDto user;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);

        properties = new JwtProperties();
        properties.setSecret(Base64.getEncoder().encodeToString(keyBytes));
        properties.setAccessTokenExpiration(Duration.ofMinutes(15));
        properties.setRefreshTokenExpiration(Duration.ofDays(7));

        provider = new JwtTokenProvider(properties);
        user = new UserDto(
                UUID.randomUUID(),
                "tester",
                "tester@example.com",
                Role.USER,
                null,
                false
        );
    }

    @Test
    void accessToken_발급_사용자ID_추출() {
        String token = provider.generateAccessToken(user);

        assertThat(provider.validateAccessToken(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(user.id());
        assertThat(provider.validateRefreshToken(token)).isFalse();
    }

    @Test
    void refreshToken_발급과_사용자ID_추출() {
        String token = provider.generateRefreshToken(user);

        assertThat(provider.validateRefreshToken(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(user.id());
        assertThat(provider.validateAccessToken(token)).isFalse();
    }

    @Test
    void 서명이_변조된_토큰은_거부한다() {
        String token = provider.generateAccessToken(user);

        int signatureStart = token.lastIndexOf(".") + 1;
        char original = token.charAt(signatureStart);
        char replacement = original == 'A' ? 'B' : 'A';

        String tamperedToken = token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);

        assertThat(provider.validateAccessToken(tamperedToken)).isFalse();
    }

    @Test
    void 만료된_토큰은_거부한다() {
        properties.setAccessTokenExpiration(Duration.ofMinutes(-1));
        properties.setRefreshTokenExpiration(Duration.ofMinutes(-1));

        String accessToken = provider.generateAccessToken(user);
        String refreshToken = provider.generateRefreshToken(user);

        assertThat(provider.validateAccessToken(accessToken)).isFalse();
        assertThat(provider.validateRefreshToken(refreshToken)).isFalse();
    }







}
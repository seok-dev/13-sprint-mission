package com.sprint.mission.discodeit.security.jwt;

import com.sprint.mission.discodeit.config.JwtProperties;
import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.exception.auth.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryJwtRegistryTest {

    private JwtProperties properties;
    private JwtTokenProvider provider;
    private InMemoryJwtRegistry registry;
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
        registry = new InMemoryJwtRegistry(provider);

        user = new UserDto(UUID.randomUUID(), "tester", "tester@example.com",
                Role.USER, null, false
        );
    }

    private JwtInformation issueTokens() {
        return new JwtInformation(
                user,
                provider.generateAccessToken(user),
                provider.generateRefreshToken(user)
        );
    }

    @Test
    void 다시_로그인하면_기존_토큰은_무효화된다() {
        JwtInformation first = issueTokens();
        registry.registerJwtInformation(first);

        JwtInformation second = issueTokens();
        registry.registerJwtInformation(second);

        assertThat(registry.hasActiveJwtInformationByAccessToken(
                first.getAccessToken())).isFalse();
        assertThat(registry.hasActiveJwtInformationByRefreshToken(
                first.getRefreshToken())).isFalse();

        assertThat(registry.hasActiveJwtInformationByAccessToken(
                second.getAccessToken())).isTrue();
        assertThat(registry.hasActiveJwtInformationByRefreshToken(
                second.getRefreshToken())).isTrue();
        assertThat(registry.hasActiveJwtInformationByUserId(user.id())).isTrue();
    }

    @Test
    void 로테이션한_이전_토큰은_재사용할_수_없다() {
        JwtInformation oldInfo = issueTokens();
        registry.registerJwtInformation(oldInfo);

        JwtInformation newInfo = issueTokens();
        registry.rotateJwtInformation(oldInfo.getRefreshToken(), newInfo);

        assertThat(registry.hasActiveJwtInformationByAccessToken(
                oldInfo.getAccessToken())).isFalse();
        assertThat(registry.hasActiveJwtInformationByRefreshToken(
                oldInfo.getRefreshToken())).isFalse();

        assertThat(registry.hasActiveJwtInformationByAccessToken(
                newInfo.getAccessToken())).isTrue();
        assertThat(registry.hasActiveJwtInformationByRefreshToken(
                newInfo.getRefreshToken())).isTrue();

        assertThatThrownBy(() ->
                registry.rotateJwtInformation(
                        oldInfo.getRefreshToken(), issueTokens()
                )
        ).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(registry.hasActiveJwtInformationByRefreshToken(
                newInfo.getRefreshToken())).isTrue();
    }

    @Test
    void 리프레시_토큰이_만료된_등록_정보를_삭제한다() {
        properties.setRefreshTokenExpiration(Duration.ofMinutes(-1));
        JwtInformation info = issueTokens();
        registry.registerJwtInformation(info);

        // Access Token은 아직 유효하고 Registry에도 등록되어 있다.
        assertThat(registry.hasActiveJwtInformationByAccessToken(
                info.getAccessToken())).isTrue();

        registry.clearExpiredJwtInformation();

        // 토큰 자체는 유효하지만 등록 정보가 삭제되어 사용할 수 없다.
        assertThat(provider.validateAccessToken(info.getAccessToken())).isTrue();
        assertThat(registry.hasActiveJwtInformationByAccessToken(
                info.getAccessToken())).isFalse();
        assertThat(registry.hasActiveJwtInformationByUserId(user.id())).isFalse();
    }

    @Test
    void 특정_사용자를_무효화해도_다른_사용자는_유지된다() {
        JwtInformation first = issueTokens();
        registry.registerJwtInformation(first);

        UserDto otherUser = new UserDto(UUID.randomUUID(), "tester",
                "tester@example.com",
                Role.USER, null, false
        );

        JwtInformation other = new JwtInformation(
                otherUser,
                provider.generateAccessToken(otherUser),
                provider.generateRefreshToken(otherUser)
        );

        registry.registerJwtInformation(other);

        registry.invalidateJwtInformationByUserId(user.id());

        // 대상 사용자의 로그인 정보와 토큰은 무효화된다.
        assertThat(registry.hasActiveJwtInformationByUserId(user.id())).isFalse();
        assertThat(registry.hasActiveJwtInformationByAccessToken(
                first.getAccessToken())).isFalse();
        assertThat(registry.hasActiveJwtInformationByRefreshToken(
                first.getRefreshToken())).isFalse();

        // 다른 사용자의 로그인 정보와 토큰은 유지된다.
        assertThat(registry.hasActiveJwtInformationByUserId(otherUser.id())).isTrue();
        assertThat(registry.hasActiveJwtInformationByAccessToken(
                other.getAccessToken())).isTrue();
        assertThat(registry.hasActiveJwtInformationByRefreshToken(
                other.getRefreshToken())).isTrue();

    }



}
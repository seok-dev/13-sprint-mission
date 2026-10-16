package com.sprint.mission.discodeit.service;

import com.sprint.mission.discodeit.dto.command.user.UserCreateCommand;
import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.security.jwt.JwtInformation;
import com.sprint.mission.discodeit.security.jwt.JwtRegistry;
import com.sprint.mission.discodeit.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class UserCacheIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtRegistry jwtRegistry;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private UserRepository userRepository;

    private UserDto user;

    @BeforeEach
    void setUp() {
        String username = "cache-" + UUID.randomUUID();

        user = userService.createUser(
                new UserCreateCommand(
                        username,
                        username + "@example.com",
                        "test-password"
                ),
                null
        );

        cacheManager.getCache("users").clear();
        clearInvocations(userRepository);
    }

    @AfterEach
    void tearDown() {
        jwtRegistry.invalidateJwtInformationByUserId(user.id());
        userRepository.deleteById(user.id());

        cacheManager.getCache("users").clear();
        cacheManager.getCache("channels").clear();
    }

    @Test
    void 사용자_목록을_반복_조회하면_캐시를_사용한다() {
        var first = userService.findAllUser();
        var second = userService.findAllUser();

        assertThat(first)
                .extracting(UserDto::id)
                .contains(user.id());
        assertThat(second).isEqualTo(first);

        verify(userRepository, times(1)).findAll();
    }

    @Test
    void 로그인과_로그아웃_후_온라인_상태를_갱신한다() {
        // 오프라인 상태의 목록을 캐시에 저장한다.
        assertThat(findTestUser().online()).isFalse();

        jwtRegistry.registerJwtInformation(
                new JwtInformation(
                        user,
                        jwtTokenProvider.generateAccessToken(user),
                        jwtTokenProvider.generateRefreshToken(user)
                )
        );

        // 로그인으로 기존 캐시가 무효화되어야 한다.
        assertThat(findTestUser().online()).isTrue();

        jwtRegistry.invalidateJwtInformationByUserId(user.id());

        // 로그아웃 후 다시 오프라인으로 조회되어야 한다.
        assertThat(findTestUser().online()).isFalse();
    }

    private UserDto findTestUser() {
        return userService.findAllUser().stream()
                .filter(dto -> dto.id().equals(user.id()))
                .findFirst()
                .orElseThrow();
    }


}

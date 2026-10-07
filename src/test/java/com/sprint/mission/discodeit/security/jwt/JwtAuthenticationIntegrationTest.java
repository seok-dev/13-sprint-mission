package com.sprint.mission.discodeit.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.discodeit.dto.auth.UserRoleUpdateRequest;
import com.sprint.mission.discodeit.dto.command.user.UserCreateCommand;
import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(properties = {
        // 테스트 전용 키. 운영 환경에서는 사용하지 않습니다.
        "discodeit.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "discodeit.storage.type=local"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationIntegrationTest {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Test
    void 로그인으로_발급한_토큰으로_API를_호출한다() throws Exception {
        // AdminInitializer가 생성하는 계정으로 실제 로그인
        MvcResult loginResult = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("username", "admin")
                                .param("password", "admin1234")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userDto.username").value("admin"))
                .andExpect(jsonPath("$.userDto.online").value(true))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        assertThat(loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header)
                        .startsWith("REFRESH_TOKEN=")
                        .contains("HttpOnly")
                        .contains("Path=/api/auth"));

        String accessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        // Access Token이 있으면 인증 성공
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 앞에서 로그인했더라도 토큰 없이 새 요청을 보내면 인증 실패
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());

        // 잘못된 토큰도 인증 실패
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 재발급하면_새_토큰만_사용할_수_있다() throws Exception {
        // 1. 로그인해서 기존 토큰 쌍을 받는다.
        MvcResult loginResult = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("username", "admin")
                                .param("password", "admin1234")
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andReturn();

        String oldAccessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        Cookie oldRefreshCookie = loginResult.getResponse()
                .getCookie(JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME);

        assertThat(oldRefreshCookie).isNotNull();

        // 2. Access Token 없이 Refresh Token 쿠키만으로 재발급한다.
        MvcResult refreshResult = mockMvc.perform(
                        post("/api/auth/refresh")
                                .cookie(oldRefreshCookie)
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userDto.username").value("admin"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String newAccessToken = objectMapper
                .readTree(refreshResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        Cookie newRefreshCookie = refreshResult.getResponse()
                .getCookie(JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME);

        assertThat(newRefreshCookie).isNotNull();
        assertThat(newAccessToken).isNotEqualTo(oldAccessToken);
        assertThat(newRefreshCookie.getValue())
                .isNotEqualTo(oldRefreshCookie.getValue());

        // 3. 이전 Access Token은 거부되고 새 토큰은 허용된다.
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isOk());

        // 4. 이전 Refresh Token은 재사용할 수 없다.
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(oldRefreshCookie)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 5. 새 Refresh Token은 재발급에 사용할 수 있다.
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(newRefreshCookie)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void 리프레시_쿠키가_없거나_잘못되면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(
                                JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME,
                                "invalid-token"
                        ))
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 로그아웃하면_쿠키가_삭제되고_기존_토큰이_무효화된다() throws Exception {
        // 1. 로그인

        MvcResult loginResult = mockMvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin")
                        .param("password", "admin1234")
                        .with(csrf())
        ).andExpect(status().isOk()).andReturn();

        String accessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        Cookie refreshCookie = loginResult.getResponse()
                .getCookie(JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME);

        assertThat(refreshCookie).isNotNull();

        UUID userId = UUID.fromString(
                objectMapper.readTree(loginResult.getResponse().getContentAsString())
                        .path("userDto")
                        .path("id")
                        .asText()
        );

        assertThat(userService.findByUserId(userId).online()).isTrue();

        // 2. Access Token 없이 Refresh Token 쿠키로 로그아웃
        MvcResult logoutResult = mockMvc.perform(
                        post("/api/auth/logout")
                                .cookie(refreshCookie)
                                .with(csrf())
                )
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie deletedCookie = logoutResult.getResponse()
                .getCookie(JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME);

        assertThat(deletedCookie).isNotNull();
        assertThat(deletedCookie.getMaxAge()).isZero();
        assertThat(deletedCookie.getPath()).isEqualTo("/api/auth");
        assertThat(deletedCookie.getValue()).isEmpty();

        // 3. 기존 Access Token으로 API 호출 불가
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // 4. 기존 Refresh Token으로 재발급 불가
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 쿠키가_없거나_잘못되어도_로그아웃은_완료된다() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(
                                JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME,
                                "invalid-token"
                        ))
                        .with(csrf()))
                .andExpect(status().isNoContent());



    }

    @Test
    void 권한이_변경되면_기존_토큰이_무효화된다() throws Exception {
        // 1. 테스트용 일반 사용자 생성
        String username = "role-" + UUID.randomUUID();
        String password = "test1234!";

        UserDto target = userService.createUser(
                new UserCreateCommand(
                        username,
                        username + "@example.com",
                        password
                ),
                null
        );

        // 2. 일반 사용자 로그인
        MvcResult userLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        String userAccessToken = objectMapper
                .readTree(userLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        Cookie userRefreshCookie = userLogin.getResponse()
                .getCookie(JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME);

        assertThat(userRefreshCookie).isNotNull();
        assertThat(userService.findByUserId(target.id()).online()).isTrue();

        // 3. 관리자 로그인
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin")
                        .param("password", "admin1234")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        String adminAccessToken = objectMapper
                .readTree(adminLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // 4. 관리자가 대상 사용자의 권한 변경
        UserRoleUpdateRequest request =
                new UserRoleUpdateRequest(target.id(), Role.CHANNEL_MANAGER);

        mockMvc.perform(put("/api/auth/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CHANNEL_MANAGER"))
                .andExpect(jsonPath("$.online").value(false));

        assertThat(userService.findByUserId(target.id()).online()).isFalse();

        // 5. 대상 사용자의 기존 토큰 두 개 모두 거부
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(userRefreshCookie)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 6. 관리자는 로그인 상태 유지
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk());

        // 7. 대상 사용자가 다시 로그인하면 변경된 권한으로 응답
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userDto.role").value("CHANNEL_MANAGER"))
                .andExpect(jsonPath("$.userDto.online").value(true));
    }



}

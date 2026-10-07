package com.sprint.mission.discodeit.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.discodeit.config.JwtProperties;
import com.sprint.mission.discodeit.dto.auth.JwtDto;
import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.security.DiscodeitUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRegistry jwtRegistry;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;



    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication
    ) throws IOException {
        DiscodeitUserDetails userDetails = (DiscodeitUserDetails) authentication.getPrincipal();

        UserDto original = userDetails.getUserDto();
        UserDto userDto = new UserDto(
                original.id(),
                original.username(),
                original.email(),
                original.role(),
                original.profile(),
                true
        );

        String accessToken = jwtTokenProvider.generateAccessToken(userDto);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDto);

        jwtRegistry.registerJwtInformation(
                new JwtInformation(userDto, accessToken, refreshToken)
        );

        ResponseCookie cookie = ResponseCookie
                .from(JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(jwtProperties.getRefreshTokenExpiration())
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
                response.getWriter(),
                new JwtDto(userDto, accessToken)
        );
    }
}

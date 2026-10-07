package com.sprint.mission.discodeit.security.jwt;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtLogoutHandler implements LogoutHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRegistry jwtRegistry;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            Arrays.stream(cookies)
                    .filter(cookie -> JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME
                            .equals(cookie.getName()))
                    .findFirst()
                    .ifPresent(cookie -> invalidateToken(cookie.getValue()));
        }

        ResponseCookie expiredCookie = ResponseCookie
                .from(JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }

    private void invalidateToken(String refreshToken) {
        try {
            synchronized (jwtRegistry) {
                if (!jwtRegistry.hasActiveJwtInformationByRefreshToken(refreshToken)) {
                    return;
                }

                UUID userId = jwtTokenProvider.getUserId(refreshToken);
                jwtRegistry.invalidateJwtInformationByUserId(userId);
            }
        }  catch (JwtException | IllegalArgumentException e) {

        }
    }
}

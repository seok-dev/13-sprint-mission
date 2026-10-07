package com.sprint.mission.discodeit.controller;


import com.sprint.mission.discodeit.config.JwtProperties;
import com.sprint.mission.discodeit.dto.auth.JwtDto;
import com.sprint.mission.discodeit.dto.auth.UserRoleUpdateRequest;
import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.security.jwt.JwtInformation;
import com.sprint.mission.discodeit.security.jwt.JwtTokenProvider;
import com.sprint.mission.discodeit.service.JwtService;
import com.sprint.mission.discodeit.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {


    private final UserService userService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @PostMapping("/refresh")
    public ResponseEntity<JwtDto> refresh(
            @CookieValue(
                    name = JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME,
                    required = false
            ) String refreshToken,
            HttpServletRequest request
    ) {
        JwtInformation information = jwtService.refresh(refreshToken);

        ResponseCookie cookie = ResponseCookie.from(
                JwtTokenProvider.REFRESH_TOKEN_COOKIE_NAME,
                information.getRefreshToken()
        )
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(jwtProperties.getRefreshTokenExpiration())
                .build();

        JwtDto response = new JwtDto(
                information.getUserDto(),
                information.getAccessToken()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }


    @GetMapping("/csrf-token")
    public ResponseEntity<Void> getCsrfToken(CsrfToken csrfToken){
        String tokenValue = csrfToken.getToken();
        log.debug("CSRF 토큰 요청: {}", tokenValue);
        return ResponseEntity.status(203).build();
    }


    @PutMapping("/role")
    public ResponseEntity<UserDto> role(@RequestBody UserRoleUpdateRequest request){
        UserDto update = userService.updateRole(request.userId(), request.newRole());
        return ResponseEntity.ok(update);
    }
}

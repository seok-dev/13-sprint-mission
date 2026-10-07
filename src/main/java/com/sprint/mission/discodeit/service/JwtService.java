package com.sprint.mission.discodeit.service;

import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.exception.auth.InvalidRefreshTokenException;
import com.sprint.mission.discodeit.exception.user.UserNotFoundException;
import com.sprint.mission.discodeit.security.jwt.JwtInformation;
import com.sprint.mission.discodeit.security.jwt.JwtRegistry;
import com.sprint.mission.discodeit.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRegistry jwtRegistry;
    private final UserService userService;


    public JwtInformation refresh(String refreshToken) {
        try {
            if (!jwtRegistry.hasActiveJwtInformationByRefreshToken(refreshToken)) {
                throw new InvalidRefreshTokenException();
            }
            UUID userId = jwtTokenProvider.getUserId(refreshToken);
            UserDto original = userService.findByUserId(userId);

            UserDto userDto = new UserDto(
                    original.id(),
                    original.username(),
                    original.email(),
                    original.role(),
                    original.profile(),
                    true
            );

            String newAccessToken =
                    jwtTokenProvider.generateAccessToken(userDto);
            String newRefreshToken =
                    jwtTokenProvider.generateRefreshToken(userDto);

            JwtInformation newInformation = new JwtInformation(
                    userDto,
                    newAccessToken,
                    newRefreshToken
            );

            jwtRegistry.rotateJwtInformation(refreshToken, newInformation);

            return newInformation;

        } catch (JwtException | IllegalArgumentException | UserNotFoundException e) {
            throw new InvalidRefreshTokenException();
        }
    }


}

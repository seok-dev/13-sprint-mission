package com.sprint.mission.discodeit.security.jwt;

import com.sprint.mission.discodeit.exception.auth.InvalidRefreshTokenException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryJwtRegistry implements JwtRegistry {

    private final JwtTokenProvider jwtTokenProvider;

    public InMemoryJwtRegistry(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    private final Map<UUID, Queue<JwtInformation>> origin =
            new ConcurrentHashMap<>();

    private final int maxActiveJwtCount = 1;


    @Override
    public synchronized void registerJwtInformation(JwtInformation jwtInformation) {
        UUID userId = jwtInformation.getUserDto().id();

        Queue<JwtInformation> queue =
                origin.computeIfAbsent(userId, id -> new ArrayDeque<>());

        while (queue.size() >= maxActiveJwtCount) {
            queue.poll();
        }
        queue.offer(jwtInformation);
    }

    @Override
    public synchronized void invalidateJwtInformationByUserId(UUID userId) {
        origin.remove(userId);
    }

    @Override
    public synchronized boolean hasActiveJwtInformationByUserId(UUID userId) {
        Queue<JwtInformation> queue = origin.get(userId);

        if (queue == null) {
            return false;
        }

        return queue.stream()
                .anyMatch(info -> jwtTokenProvider.validateRefreshToken(
                        info.getRefreshToken()
                ));
    }

    @Override
    public synchronized boolean hasActiveJwtInformationByAccessToken(String accessToken) {
        if (!jwtTokenProvider.validateAccessToken(accessToken)) {
            return false;
        }
        return origin.values().stream()
                .flatMap(Queue::stream)
                .anyMatch(info -> info.getAccessToken().equals(accessToken));
    }

    @Override
    public synchronized boolean hasActiveJwtInformationByRefreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            return false;
        }
        return origin.values().stream()
                .flatMap(Queue::stream)
                .anyMatch(info -> info.getRefreshToken().equals(refreshToken));
    }

    @Override
    public synchronized void rotateJwtInformation(String refreshToken, JwtInformation newJwtInformation) {
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new InvalidRefreshTokenException();
        }
        UUID userId = jwtTokenProvider.getUserId(refreshToken);

        if (!userId.equals(newJwtInformation.getUserDto().id())) {
            throw new InvalidRefreshTokenException();
        }

        Queue<JwtInformation> queue = origin.get(userId);

        if (queue == null) {
            throw new InvalidRefreshTokenException();
        }

        JwtInformation existing = queue.stream()
                .filter(info -> info.getRefreshToken().equals(refreshToken))
                .findFirst()
                .orElseThrow(InvalidRefreshTokenException::new);
        queue.remove(existing);
        queue.offer(newJwtInformation);
    }

    @Override
    @Scheduled(fixedDelay = 1000 * 60 * 5)
    public synchronized void clearExpiredJwtInformation() {
        origin.values().forEach(queue ->
                queue.removeIf(info ->
                        !jwtTokenProvider.validateRefreshToken(
                                info.getRefreshToken()
                        )
                )
        );

        origin.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

}

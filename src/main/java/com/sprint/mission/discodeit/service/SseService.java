package com.sprint.mission.discodeit.service;

import com.sprint.mission.discodeit.dto.sse.SseMessage;
import com.sprint.mission.discodeit.repository.SseEmitterRepository;
import com.sprint.mission.discodeit.repository.SseMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SseService {

    private static final long CONNECTION_TIMEOUT =
            Duration.ofHours(1).toMillis();

    private static final Duration MESSAGE_RETENTION = Duration.ofHours(1);

    private final SseEmitterRepository emitterRepository;
    private final SseMessageRepository messageRepository;

    @Scheduled(fixedDelay = 1000 * 60 * 30)
    public synchronized void cleanUp() {
        int removedCount = 0;
        for (SseEmitter emitter : emitterRepository.findAll()) {
            if (!ping(emitter)) {
                emitterRepository.delete(emitter);
                removedCount++;
            }
        }

        Instant cutoff = Instant.now().minus(MESSAGE_RETENTION);
        messageRepository.deleteOlderThan(cutoff);

        log.debug("SSE 정리 완료 - 제거된 연결 수: {}", removedCount);
    }

    public synchronized SseEmitter connect(
            UUID receiverId,
            UUID lastEventId
    ) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT);

        emitter.onCompletion(
                () -> emitterRepository.delete(receiverId, emitter)
        );

        emitter.onTimeout(() -> {
            emitterRepository.delete(receiverId, emitter);
            emitter.complete();
        });

        emitter.onError(
                error -> emitterRepository.delete(receiverId, emitter)
        );

        emitterRepository.save(receiverId, emitter);

        if (!ping(emitter)) {
            emitterRepository.delete(receiverId, emitter);
            return emitter;
        }

        for (SseMessage message :
                messageRepository.findAllAfter(receiverId, lastEventId)) {

            if (!sendToEmitter(emitter, message)) {
                emitterRepository.delete(receiverId, emitter);
                return emitter;
            }
        }

        log.debug("SSE 연결 등록 - receiverId: {}", receiverId);
        return emitter;
    }

    private boolean ping(SseEmitter emitter) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .comment("ping")
            );
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE ping 실패 - 연결 종료 또는 전송 오류", e);
            return false;
        }
    }

    private boolean sendToEmitter(
            SseEmitter emitter,
            SseMessage message
    ) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .id(message.id().toString())
                            .name(message.eventName())
                            .data(message.data())
            );
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 이벤트 전송 실패 - eventId: {}", message.id(), e);
            return false;
        }
    }


    public synchronized void send(Collection<UUID> receiverIds,
                                   String eventName,
                                   Object data) {
        Set<UUID> receivers = Set.copyOf(receiverIds);

        if (receivers.isEmpty()) {
            return;
        }

        SseMessage message = new SseMessage(
                UUID.randomUUID(),
                eventName,
                data,
                receivers,
                false,
                Instant.now()
        );

        messageRepository.save(message);

        for (UUID receiverId : receivers) {
            for (SseEmitter emitter :
                    emitterRepository.findByReceiverId(receiverId)) {

                if (!sendToEmitter(emitter, message)) {
                    emitterRepository.delete(receiverId, emitter);
                }
            }
        }
    }


    public synchronized void broadcast(String eventName, Object data) {
        SseMessage message = new SseMessage(
                UUID.randomUUID(),
                eventName,
                data,
                Set.of(),
                true,
                Instant.now()
        );

        messageRepository.save(message);

        for (SseEmitter emitter : emitterRepository.findAll()) {
            if (!sendToEmitter(emitter, message)) {
                emitterRepository.delete(emitter);
            }
        }
    }

}

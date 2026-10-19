package com.sprint.mission.discodeit.service;

import com.sprint.mission.discodeit.dto.sse.SseMessage;
import com.sprint.mission.discodeit.repository.SseEmitterRepository;
import com.sprint.mission.discodeit.repository.SseMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class SseServiceTest {

    @Mock
    private SseMessageRepository messageRepository;

    private SseEmitterRepository emitterRepository;
    private SseService sseService;

    @BeforeEach
    void setUp() {
        emitterRepository = new SseEmitterRepository();
        sseService = new SseService(emitterRepository, messageRepository);
    }

    @Test
    void 지정한_사용자의_모든_연결에만_전송한다() throws IOException {
        UUID receiverId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        SseEmitter firstTab = register(receiverId);
        SseEmitter secondTab = register(receiverId);
        SseEmitter otherTab = register(otherId);

        sseService.send(
                List.of(receiverId),
                "notifications.created",
                "알림 데이터"
        );

        ArgumentCaptor<SseMessage> captor =
                ArgumentCaptor.forClass(SseMessage.class);

        // 이벤트 저장이 전송보다 먼저 수행되는지도 확인한다.
        var order = inOrder(messageRepository, firstTab, secondTab);
        order.verify(messageRepository).save(captor.capture());
        order.verify(firstTab).send(any(SseEmitter.SseEventBuilder.class));
        order.verify(secondTab).send(any(SseEmitter.SseEventBuilder.class));

        verifyNoInteractions(otherTab);

        SseMessage saved = captor.getValue();
        assertThat(saved.id()).isNotNull();
        assertThat(saved.eventName()).isEqualTo("notifications.created");
        assertThat(saved.data()).isEqualTo("알림 데이터");
        assertThat(saved.receiverIds()).containsExactly(receiverId);
        assertThat(saved.broadcast()).isFalse();
    }

    @Test
    void 전송에_실패한_연결만_삭제하고_다른_연결에는_전송한다()
            throws IOException {
        UUID receiverId = UUID.randomUUID();

        SseEmitter failedTab = register(receiverId);
        SseEmitter healthyTab = register(receiverId);

        doThrow(new IOException("연결 끊김"))
                .when(failedTab)
                .send(any(SseEmitter.SseEventBuilder.class));

        sseService.send(
                List.of(receiverId),
                "notifications.created",
                "알림 데이터"
        );

        verify(healthyTab).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(emitterRepository.findByReceiverId(receiverId))
                .containsExactly(healthyTab);
    }

    @Test
    void 전체_연결에_전송하고_실패한_연결만_삭제한다()
            throws IOException {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        SseEmitter failedTab = register(firstUserId);
        SseEmitter healthyTab = register(secondUserId);

        doThrow(new IOException("연결 끊김"))
                .when(failedTab)
                .send(any(SseEmitter.SseEventBuilder.class));

        sseService.broadcast("users.updated", "사용자 데이터");

        verify(failedTab).send(any(SseEmitter.SseEventBuilder.class));
        verify(healthyTab).send(any(SseEmitter.SseEventBuilder.class));

        assertThat(emitterRepository.findByReceiverId(firstUserId))
                .isEmpty();
        assertThat(emitterRepository.findByReceiverId(secondUserId))
                .containsExactly(healthyTab);

        ArgumentCaptor<SseMessage> captor =
                ArgumentCaptor.forClass(SseMessage.class);
        verify(messageRepository).save(captor.capture());

        SseMessage saved = captor.getValue();
        assertThat(saved.broadcast()).isTrue();
        assertThat(saved.receiverIds()).isEmpty();
        assertThat(saved.eventName()).isEqualTo("users.updated");
        assertThat(saved.data()).isEqualTo("사용자 데이터");
    }

    @Test
    void 정리할_때_끊긴_연결을_삭제하고_오래된_메시지_삭제를_요청한다()
            throws IOException {
        UUID receiverId = UUID.randomUUID();

        SseEmitter failedTab = register(receiverId);
        SseEmitter healthyTab = register(receiverId);

        doThrow(new IOException("연결 끊김"))
                .when(failedTab)
                .send(any(SseEmitter.SseEventBuilder.class));

        Instant before = Instant.now();
        sseService.cleanUp();
        Instant after = Instant.now();

        verify(healthyTab).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(emitterRepository.findByReceiverId(receiverId))
                .containsExactly(healthyTab);

        ArgumentCaptor<Instant> captor =
                ArgumentCaptor.forClass(Instant.class);
        verify(messageRepository).deleteOlderThan(captor.capture());

        assertThat(captor.getValue()).isBetween(
                before.minus(Duration.ofHours(1)),
                after.minus(Duration.ofHours(1))
        );
    }

    private SseEmitter register(UUID receiverId) {
        SseEmitter emitter = mock(SseEmitter.class);
        emitterRepository.save(receiverId, emitter);
        return emitter;
    }



}
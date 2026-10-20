package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.dto.notification.NotificationDto;
import com.sprint.mission.discodeit.event.NotificationCreatedEvent;
import com.sprint.mission.discodeit.service.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class SseRequiredEventListenerIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private SseService sseService;

    private TransactionTemplate transactionTemplate;
    private NotificationDto notification;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        notification = new NotificationDto(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                "새 메시지가 도착했습니다.",
                "안녕하세요"
        );
    }

    @Test
    void 커밋_후_알림_수신자에게_SSE_이벤트를_전송한다() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new NotificationCreatedEvent(notification)
            );

            // 커밋 전에는 전송하지 않아야 한다.
            verifyNoInteractions(sseService);
        });

        verify(sseService).send(
                List.of(notification.receiverId()),
                "notifications.created",
                notification
        );
        verifyNoMoreInteractions(sseService);
    }

    @Test
    void 롤백하면_SSE_이벤트를_전송하지_않는다() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new NotificationCreatedEvent(notification)
            );

            status.setRollbackOnly();
        });

        verifyNoInteractions(sseService);
    }
}

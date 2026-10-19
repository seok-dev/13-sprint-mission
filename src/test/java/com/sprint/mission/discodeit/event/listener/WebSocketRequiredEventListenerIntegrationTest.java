package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.dto.message.MessageDto;
import com.sprint.mission.discodeit.event.MessageCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
class WebSocketRequiredEventListenerIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private TransactionTemplate transactionTemplate;
    private MessageCreatedEvent event;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        UUID channelId = UUID.randomUUID();

        MessageDto messageDto = new MessageDto(
                UUID.randomUUID(),
                Instant.now(),
                null,
                "안녕하세요",
                channelId,
                null,
                List.of()
        );

        event = new MessageCreatedEvent(
                channelId,
                UUID.randomUUID(),
                "보낸 사람",
                "general",
                "안녕하세요",
                messageDto
        );
    }

    @Test
    void 커밋_후에만_메시지를_전송한다() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);

            verifyNoInteractions(messagingTemplate);
        });

        verify(messagingTemplate).convertAndSend(
                "/sub/channels." + event.channelId() + ".messages",
                event.messageDto()
        );
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void 롤백하면_메시지를_전송하지_않는다() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verifyNoInteractions(messagingTemplate);
    }
}

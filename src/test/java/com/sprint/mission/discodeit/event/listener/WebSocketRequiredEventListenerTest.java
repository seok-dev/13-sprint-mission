package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.dto.message.MessageDto;
import com.sprint.mission.discodeit.event.MessageCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;


@ExtendWith(MockitoExtension.class)
class WebSocketRequiredEventListenerTest {


    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketRequiredEventListener listener;

    @Test
    void 메시지를_해당_채널의_구독_경로로_전송한다() {
        UUID channelId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        MessageDto messageDto = new MessageDto(
                UUID.randomUUID(),
                Instant.now(),
                null,
                "안녕하세요",
                channelId,
                null,
                List.of()
        );

        MessageCreatedEvent event = new MessageCreatedEvent(
                channelId,
                senderId,
                "보낸 사람",
                "general",
                "안녕하세요",
                messageDto
        );

        listener.handleMessage(event);

        verify(messagingTemplate).convertAndSend(
                "/sub/channels." + channelId + ".messages",
                messageDto
        );
        verifyNoMoreInteractions(messagingTemplate);
    }

}
package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.entity.ReadStatus;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.event.MessageCreatedEvent;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRequiredEventListenerUnitTest {

    @Mock
    private ReadStatusRepository readStatusRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationRequiredEventListener listener;

    @Test
    void 메시지_작성자를_제외하고_알림을_생성한다() {
        UUID channelId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        ReadStatus sendStatus = createReadStatus(senderId);
        ReadStatus receiverStatus = createReadStatus(receiverId);

        when(readStatusRepository
                .findAllByChannelIdAndNotificationEnabledTrue(channelId))
                .thenReturn(List.of(sendStatus, receiverStatus));

        MessageCreatedEvent event = new MessageCreatedEvent(
                channelId,
                senderId,
                "보낸 사람",
                "general",
                "안녕하세요"
        );

        listener.on(event);

        verify(notificationService).create(
                receiverId,
                "보낸 사람 (#general)",
                "안녕하세요"
        );

        // 수식자 알림 외에 작성자 알림 등이 추가되지 않았는지 확인한다.
    }

    private ReadStatus createReadStatus(UUID userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);

        ReadStatus readStatus = mock(ReadStatus.class);
        when(readStatus.getUser()).thenReturn(user);

        return readStatus;
    }




}

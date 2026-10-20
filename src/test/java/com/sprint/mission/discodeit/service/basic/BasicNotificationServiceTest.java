package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.notification.NotificationDto;
import com.sprint.mission.discodeit.entity.Notification;
import com.sprint.mission.discodeit.event.NotificationCreatedEvent;
import com.sprint.mission.discodeit.mapper.NotificationMapper;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasicNotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BasicNotificationService basicNotificationService;

    @Test
    void 알림음_저장하고_생성_이벤트를_발행한다() {
        UUID receiverId = UUID.randomUUID();
        String title = "새 메시지";
        String content = "안녕하세요";

        NotificationDto expected = new NotificationDto(
                UUID.randomUUID(),
                Instant.now(),
                receiverId,
                title,
                content
        );

        when(notificationMapper.toDto(any(Notification.class)))
                .thenReturn(expected);
        NotificationDto result =
                basicNotificationService.create(receiverId, title, content);

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        var order = inOrder(
                notificationRepository,
                notificationMapper,
                eventPublisher
        );

        order.verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        order.verify(notificationMapper).toDto(saved);
        order.verify(eventPublisher).publishEvent(
                 new NotificationCreatedEvent(expected)
        );

        assertThat(result).isEqualTo(expected);
        verifyNoMoreInteractions(eventPublisher);

    }








}
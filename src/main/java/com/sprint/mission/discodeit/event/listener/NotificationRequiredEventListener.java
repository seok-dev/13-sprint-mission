package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.entity.ReadStatus;
import com.sprint.mission.discodeit.event.MessageCreatedEvent;
import com.sprint.mission.discodeit.event.RoleUpdatedEvent;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationRequiredEventListener {

    private final ReadStatusRepository readStatusRepository;
    private final NotificationService notificationService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MessageCreatedEvent event) {
        List<ReadStatus> readStatuses = readStatusRepository
                .findAllByChannelIdAndNotificationEnabledTrue(event.channelId());

        String channelName = event.channelName();

        String title = (channelName == null || channelName.isBlank())
                ? event.senderName() + "(개인 메시지)"
                : event.senderName() + " (#" + channelName + ")";


        for (ReadStatus readStatus : readStatuses) {
            UUID receiverId = readStatus.getUser().getId();

            if (receiverId.equals(event.senderId())) {
                continue;
            }

            notificationService.create(
                    receiverId,
                    title,
                    event.content()
            );
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RoleUpdatedEvent event) {
        notificationService.create(
                event.userId(),
                "권한이 변경되었습니다.",
                event.oldRole().name() + " -> " + event.newRole().name()
        );
    }





}

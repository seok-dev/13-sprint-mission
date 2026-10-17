package com.sprint.mission.discodeit.event.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.discodeit.entity.ReadStatus;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.event.MessageCreatedEvent;
import com.sprint.mission.discodeit.event.RoleUpdatedEvent;
import com.sprint.mission.discodeit.event.S3UploadFailedEvent;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "discodeit.kafka.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationRequiredTopicListener {

    private final ObjectMapper objectMapper;
    private final ReadStatusRepository  readStatusRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @KafkaListener(topics = "discodeit.MessageCreatedEvent")
    public void onMessageCreatedEvent(String payload) {
        MessageCreatedEvent event = deserialize(payload, MessageCreatedEvent.class);

        String channelName = event.channelName();
        String title = (channelName == null || channelName.isBlank())
                ? event.senderName() + " (개인 메시지)"
                : event.senderName() + " (#" + channelName + ")";

        for (ReadStatus readStatus : readStatusRepository
                .findAllByChannelIdAndNotificationEnabledTrue(
                event.channelId()
        )) {
            UUID receiverId =  readStatus.getUser().getId();

            if (receiverId.equals(event.senderId())) {
                continue;
            }

            notificationService.create(
                    receiverId,
                    title,
                    event.content()
            );
        }

        log.info("메시지 이벤트 처리 완료 - channelId: {}", event.channelId());
    }

    @KafkaListener(topics = "discodeit.RoleUpdatedEvent")
    public void onRoleUpdatedEvent(String payload) {
        RoleUpdatedEvent event = deserialize(payload, RoleUpdatedEvent.class);

        notificationService.create(
                event.userId(),
                "권한이 변경되었습니다.",
                event.oldRole().name() + " -> " + event.newRole().name()
        );

        log.info("권한 변경 이벤트 처리 완료 - userId: {}", event.userId());
    }

    @KafkaListener(topics = "discodeit.S3UploadFailedEvent")
    public void onS3UploadFailedEvent(String payload) {
        S3UploadFailedEvent event = deserialize(payload, S3UploadFailedEvent.class);

        String content = """
                Operation: %s
                RequestId: %s
                BinaryContentId: %s
                Error: %s
                """.formatted(
                event.operation(),
                event.requestId() != null ? event.requestId() : "없음",
                event.binaryContentId(),
                event.errorMessage()
        );
        for (User admin : userRepository.findAllByRole(Role.ADMIN)) {
            notificationService.create(
                    admin.getId(),
                    "S3 파일 업로드 실패",
                    content
            );
        }
        log.info("S3 실패 이벤트 처리 완료 - binaryContentId: {}, requestId: {}", event.binaryContentId(), event.requestId());
    }

    private <T> T deserialize(String payload, Class<T> eventType) {
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Kafka 이벤트 역직렬화 실패 - type: " + eventType.getName(), e
            );
        }
    }




}

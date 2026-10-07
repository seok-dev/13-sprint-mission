package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.notification.NotificationDto;
import com.sprint.mission.discodeit.entity.Notification;
import com.sprint.mission.discodeit.exception.notification.NotificationNotFoundException;
import com.sprint.mission.discodeit.mapper.NotificationMapper;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import com.sprint.mission.discodeit.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BasicNotificationService implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;


    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationDto create(UUID receiverId,
                                  String title,
                                  String content) {
        Notification notification = new Notification(receiverId, title, content);
        notificationRepository.save(notification);
        return notificationMapper.toDto(notification);
    }

    @Override
    public List<NotificationDto> findAllByReceiverId(UUID receiverId) {
        return notificationRepository
                .findAllByReceiverIdOrderByCreatedAtDesc(receiverId)
                .stream()
                .map(notificationMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID notificationId, UUID requesterId) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(()-> new NotificationNotFoundException(notificationId)

                );

        if (!notification.getReceiverId().equals(requesterId)) {
            throw new AccessDeniedException("본인의 알림만 확인할 수 있습니다.");
        }

        notificationRepository.delete(notification);
    }
}

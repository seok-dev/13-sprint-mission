package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.entity.Notification;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.event.RoleUpdatedEvent;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@ActiveProfiles("test")
class NotificationRequiredEventListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private UUID receiverId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        receiverId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll(
                notificationRepository
                        .findAllByReceiverIdOrderByCreatedAtDesc(receiverId)
        );
    }

    @Test
    void 권한_변경_알림은_커밋_후에_저장된다() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new RoleUpdatedEvent(
                            receiverId,
                            Role.USER,
                            Role.CHANNEL_MANAGER
                    )
            );

            // 아직 커밋 전이므로 알림이 없어야 한다.
            assertThat(notificationRepository
                    .findAllByReceiverIdOrderByCreatedAtDesc(receiverId))
                    .isEmpty();
        });

        // 커밋과 동기 리스너 실행이 완료된 뒤 확인한다.
        List<Notification> notifications = notificationRepository
                .findAllByReceiverIdOrderByCreatedAtDesc(receiverId);

        assertThat(notifications).hasSize(1);

        Notification notification = notifications.get(0);
        assertThat(notification.getReceiverId()).isEqualTo(receiverId);
        assertThat(notification.getTitle())
                .isEqualTo("권한이 변경되었습니다.");
        assertThat(notification.getContent())
                .isEqualTo("USER -> CHANNEL_MANAGER");
    }

    @Test
    void 롤백하면_권한_변경_알림을_저장하지_않는다() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new RoleUpdatedEvent(
                            receiverId,
                            Role.USER,
                            Role.CHANNEL_MANAGER
                    )
            );

            status.setRollbackOnly();
        });

        assertThat(notificationRepository
                .findAllByReceiverIdOrderByCreatedAtDesc(receiverId))
                .isEmpty();
    }







}
package com.sprint.mission.discodeit.service;

import com.sprint.mission.discodeit.dto.notification.NotificationDto;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class NotificationCacheIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    private UUID receiverId;

    @BeforeEach
    void setUp() {
        receiverId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll(
                notificationRepository
                        .findAllByReceiverIdOrderByCreatedAtDesc(receiverId)
        );

        cacheManager.getCache("notifications").evict(receiverId);
    }

    @Test
    void 같은_사용자의_목록은_캐시에서_반환한다() {
        NotificationDto created = notificationService.create(
                receiverId, "제목", "내용"
        );

        var first = notificationService.findAllByReceiverId(receiverId);
        var second = notificationService.findAllByReceiverId(receiverId);

        assertThat(first)
                .extracting(NotificationDto::id)
                .containsExactly(created.id());
        assertThat(second).isEqualTo(first);

        verify(notificationRepository, times(1))
                .findAllByReceiverIdOrderByCreatedAtDesc(receiverId);
    }

    @Test
    void 알림을_생성하면_기존_빈_목록_캐시를_무효화한다() {
        assertThat(notificationService.findAllByReceiverId(receiverId))
                .isEmpty();

        NotificationDto created = notificationService.create(
                receiverId, "새 알림", "내용"
        );

        assertThat(notificationService.findAllByReceiverId(receiverId))
                .extracting(NotificationDto::id)
                .containsExactly(created.id());

        verify(notificationRepository, times(2))
                .findAllByReceiverIdOrderByCreatedAtDesc(receiverId);
    }

    @Test
    void 알림을_삭제하면_기존_목록_캐시를_무효화한다() {
        NotificationDto created = notificationService.create(
                receiverId, "삭제할 알림", "내용"
        );

        assertThat(notificationService.findAllByReceiverId(receiverId))
                .hasSize(1);

        notificationService.delete(created.id(), receiverId);

        assertThat(notificationService.findAllByReceiverId(receiverId))
                .isEmpty();

        verify(notificationRepository, times(2))
                .findAllByReceiverIdOrderByCreatedAtDesc(receiverId);
    }

    @Test
    void 삭제가_롤백되면_데이터와_기존_캐시를_유지한다() {
        NotificationDto created = notificationService.create(
                receiverId, "유지할 알림", "내용"
        );

        // 기존 목록을 캐시에 저장한다.
        notificationService.findAllByReceiverId(receiverId);

        var cache = cacheManager.getCache("notifications");
        assertThat(cache).isNotNull();
        assertThat(cache.get(receiverId)).isNotNull();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            notificationService.delete(created.id(), receiverId);

            // 커밋 전에는 캐시 삭제가 실행되지 않아야 한다.
            assertThat(cache.get(receiverId)).isNotNull();

            status.setRollbackOnly();
        });

        // DB의 삭제가 롤백되었는지 확인한다.
        assertThat(notificationRepository.existsById(created.id())).isTrue();

        // 캐시도 유지되어 Repository 재조회 없이 반환된다.
        assertThat(notificationService.findAllByReceiverId(receiverId))
                .extracting(NotificationDto::id)
                .containsExactly(created.id());

        verify(notificationRepository, times(1))
                .findAllByReceiverIdOrderByCreatedAtDesc(receiverId);
    }


}

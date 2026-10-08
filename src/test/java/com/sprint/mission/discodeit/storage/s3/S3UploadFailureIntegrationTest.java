package com.sprint.mission.discodeit.storage.s3;

import com.sprint.mission.discodeit.dto.binarycontent.BinaryContentDto;
import com.sprint.mission.discodeit.dto.command.binarycontent.BinaryContentCreateCommand;
import com.sprint.mission.discodeit.entity.BinaryContentStatus;
import com.sprint.mission.discodeit.entity.Notification;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.repository.BinaryContentRepository;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.service.BinaryContentService;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "discodeit.storage.type=s3",
        "discodeit.storage.s3.region=ap-northeast-2",
        "discodeit.storage.s3.bucket=test-bucket",
        "discodeit.storage.s3.presigned-url-expiration=600",
        "spring.datasource.url=jdbc:h2:mem:s3failuretest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
class S3UploadFailureIntegrationTest {

    @Autowired
    private BinaryContentService binaryContentService;

    @Autowired
    private BinaryContentRepository binaryContentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private S3Presigner s3Presigner;

    @Test
    void 재시도가_모두_실패하면_관리자_알림과_FAIL_상태를_저장한다() {
        String username = "admin-" + UUID.randomUUID();
        User admin = userRepository.save(new User(
                username,
                username + "@example.com",
                "password",
                Role.ADMIN,
                null
        ));

        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        var failure = S3Exception.builder()
                .message("upload unavailable")
                .statusCode(503)
                .build();

        when(s3Client.putObject(
                any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(failure);

        BinaryContentDto created = null;
        String previousRequestId = MDC.get("requestId");

        try {
            MDC.put("requestId", "s3-failure-integration");

            created = binaryContentService.create(
                    new BinaryContentCreateCommand(
                            "test.txt",
                            bytes.length,
                            "text/plain",
                            bytes
                    )
            );

            UUID binaryContentId = created.id();

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(binaryContentService.find(binaryContentId).status())
                        .isEqualTo(BinaryContentStatus.FAIL);

                List<Notification> notifications = notificationRepository
                        .findAllByReceiverIdOrderByCreatedAtDesc(admin.getId());

                assertThat(notifications).hasSize(1);

                Notification notification = notifications.get(0);
                assertThat(notification.getTitle())
                        .isEqualTo("S3 파일 업로드 실패");
                assertThat(notification.getContent())
                        .contains(
                                "RequestId: s3-failure-integration",
                                "BinaryContentId: " + binaryContentId,
                                "upload unavailable"
                        );
            });

            verify(s3Client, times(3)).putObject(
                    any(PutObjectRequest.class), any(RequestBody.class)
            );
        } finally {
            if (previousRequestId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequestId);
            }

            notificationRepository.deleteAll(
                    notificationRepository
                            .findAllByReceiverIdOrderByCreatedAtDesc(admin.getId())
            );

            if (created != null) {
                binaryContentRepository.deleteById(created.id());
            }
            userRepository.deleteById(admin.getId());
        }
    }
}

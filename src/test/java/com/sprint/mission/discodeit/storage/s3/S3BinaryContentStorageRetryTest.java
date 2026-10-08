package com.sprint.mission.discodeit.storage.s3;

import com.sprint.mission.discodeit.config.RetryConfig;
import com.sprint.mission.discodeit.config.S3Properties;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.exception.storage.StorageException;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.service.NotificationService;
import com.sprint.mission.discodeit.storage.BinaryContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = {
        RetryConfig.class,
        S3BinaryContentStorage.class
})
@TestPropertySource(properties = "discodeit.storage.type=s3")
class S3BinaryContentStorageRetryTest {

    @Autowired
    private BinaryContentStorage storage;

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private S3Presigner s3Presigner;

    @MockitoBean
    private S3Properties props;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        when(props.getBucket()).thenReturn("test-bucket");
    }

    @Test
    void 두_번_실패하고_세_번째에_성공하면_알림을_생성하지_않는다() {
        UUID id = UUID.randomUUID();
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

        var failure = S3Exception.builder()
                .message("upload unavailable")
                .statusCode(503)
                .build();

        when(s3Client.putObject(
                any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(failure)
                .thenThrow(failure)
                .thenReturn(PutObjectResponse.builder().build());

        assertThat(storage.put(id, bytes)).isEqualTo(id);

        verify(s3Client, times(3)).putObject(
                any(PutObjectRequest.class), any(RequestBody.class)
        );
        verifyNoInteractions(userRepository, notificationService);
    }

    @Test
    void 모두_실패하면_관리자에게_알리고_원래_실패를_전달한다() {
        UUID id = UUID.randomUUID();
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

        User admin = new User(
                "admin", "admin@example.com", "password",
                Role.ADMIN, null
        );

        var failure = S3Exception.builder()
                .message("upload unavailable")
                .statusCode(503)
                .build();

        when(s3Client.putObject(
                any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(failure);

        when(userRepository.findAllByRole(Role.ADMIN))
                .thenReturn(List.of(admin));

        String previousRequestId = MDC.get("requestId");
        try {
            MDC.put("requestId", "retry-test-request");

            assertThatThrownBy(() -> storage.put(id, bytes))
                    .isInstanceOf(StorageException.class)
                    .hasCause(failure);
        } finally {
            if (previousRequestId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequestId);
            }
        }

        verify(s3Client, times(3)).putObject(
                any(PutObjectRequest.class), any(RequestBody.class)
        );

        ArgumentCaptor<String> contentCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(notificationService).create(
                eq(admin.getId()),
                eq("S3 파일 업로드 실패"),
                contentCaptor.capture()
        );
        verifyNoMoreInteractions(notificationService);

        assertThat(contentCaptor.getValue())
                .contains(
                        "Operation:",
                        "RequestId: retry-test-request",
                        "BinaryContentId: " + id,
                        "upload unavailable"
                );
    }
}
package com.sprint.mission.discodeit.event.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.event.S3UploadFailedEvent;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class NotificationRequiredTopicListenerTest {

    @Mock
    private ReadStatusRepository readStatusRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    private ObjectMapper objectMapper;
    private NotificationRequiredTopicListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        listener = new NotificationRequiredTopicListener(
                objectMapper,
                readStatusRepository,
                notificationService,
                userRepository
        );
    }

    @Test
    void S3_실패_JSON을_받으면_모든_관리자에게_알림을_생성한다()
            throws Exception {
        User firstAdmin = new User(
                "admin1", "admin1@example.com", "password",
                Role.ADMIN, null
        );
        User secondAdmin = new User(
                "admin2", "admin2@example.com", "password",
                Role.ADMIN, null
        );

        when(userRepository.findAllByRole(Role.ADMIN))
                .thenReturn(List.of(firstAdmin, secondAdmin));

        UUID binaryContentId = UUID.randomUUID();

        S3UploadFailedEvent event = new S3UploadFailedEvent(
                binaryContentId,
                "kafka-test-request",
                "S3 파일 업로드",
                "upload unavailable"
        );

        String payload = objectMapper.writeValueAsString(event);

        listener.onS3UploadFailedEvent(payload);

        ArgumentCaptor<String> contentCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(notificationService).create(
                eq(firstAdmin.getId()),
                eq("S3 파일 업로드 실패"),
                contentCaptor.capture()
        );

        String content = contentCaptor.getValue();

        assertThat(content).contains(
                "Operation: S3 파일 업로드",
                "RequestId: kafka-test-request",
                "BinaryContentId: " + binaryContentId,
                "Error: upload unavailable"
        );

        verify(notificationService).create(
                secondAdmin.getId(),
                "S3 파일 업로드 실패",
                content
        );

        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void 잘못된_JSON이면_알림을_생성하지_않고_예외를_전달한다() {
        assertThatThrownBy(
                () -> listener.onS3UploadFailedEvent("{invalid-json")
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(userRepository, notificationService);
    }



}
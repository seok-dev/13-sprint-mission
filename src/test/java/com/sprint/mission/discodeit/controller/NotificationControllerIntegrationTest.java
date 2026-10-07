package com.sprint.mission.discodeit.controller;

import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.entity.Notification;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.repository.NotificationRepository;
import com.sprint.mission.discodeit.security.DiscodeitUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    private UUID receiverId;
    private DiscodeitUserDetails userDetails;

    @BeforeEach
    void setUp() {
        receiverId = UUID.randomUUID();

        UserDto userDto = new UserDto(
                receiverId,
                "receiver",
                "receiver@example.com",
                Role.USER,
                null,
                true
        );

        userDetails = new DiscodeitUserDetails(userDto, null);
    }

    @Test
    void 본인의_알림만_조회한다() throws Exception {
        notificationRepository.save(
                new Notification(receiverId, "내 알림", "메시지 내용")
        );
        notificationRepository.save(
                new Notification(UUID.randomUUID(), "다른 사람 알림", "내용")
        );

        mockMvc.perform(get("/api/notifications")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].receiverId")
                        .value(receiverId.toString()))
                .andExpect(jsonPath("$[0].title").value("내 알림"))
                .andExpect(jsonPath("$[0].content").value("메시지 내용"));
    }

    @Test
    void 본인의_알림을_삭제한다() throws Exception {
        Notification notification = notificationRepository.save(
                new Notification(receiverId, "내 알림", "내용")
        );

        mockMvc.perform(delete("/api/notifications/{notificationId}",
                        notification.getId())
                        .with(user(userDetails))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.existsById(notification.getId()))
                .isFalse();
    }

    @Test
    void 다른_사람의_알림은_삭제할_수_없다() throws Exception {
        Notification notification = notificationRepository.save(
                new Notification(UUID.randomUUID(), "다른 사람 알림", "내용")
        );

        mockMvc.perform(delete("/api/notifications/{notificationId}",
                        notification.getId())
                        .with(user(userDetails))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(notificationRepository.existsById(notification.getId()))
                .isTrue();
    }

    @Test
    void 없는_알림을_삭제하면_404를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/notifications/{notificationId}",
                        UUID.randomUUID())
                        .with(user(userDetails))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 인증_없이_조회하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_없이_삭제하면_401을_반환한다() throws Exception {
        Notification notification = notificationRepository.save(
                new Notification(receiverId, "내 알림", "내용")
        );

        mockMvc.perform(delete("/api/notifications/{notificationId}",
                        notification.getId())
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        assertThat(notificationRepository.existsById(notification.getId()))
                .isTrue();
    }

}

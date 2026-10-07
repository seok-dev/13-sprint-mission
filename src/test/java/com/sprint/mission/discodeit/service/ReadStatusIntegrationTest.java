package com.sprint.mission.discodeit.service;

import com.sprint.mission.discodeit.dto.command.readstatus.ReadStatusUpdateCommand;
import com.sprint.mission.discodeit.dto.readstatus.ReadStatusDto;
import com.sprint.mission.discodeit.entity.*;
import com.sprint.mission.discodeit.repository.ChannelRepository;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReadStatusIntegrationTest {

    @Autowired
    private ReadStatusService readStatusService;

    @Autowired
    private ReadStatusRepository readStatusRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelRepository channelRepository;

    private ReadStatus createReadStatus(ChannelType type) {
        String username = "user-" + UUID.randomUUID();

        User user = userRepository.save(new User(
                username,
                username + "example.com",
                "test-password",
                Role.USER,
                null
        ));

        Channel channel = channelRepository.save(
                new Channel(type, "test-channel", "테스트 채널")
        );

        return readStatusRepository.save(new ReadStatus(user, channel));
    }

    @Test
    void 채널_종류에_따라_알림_초기값이_달라진다() {
        ReadStatus privateStatus = createReadStatus(ChannelType.PRIVATE);
        ReadStatus publicStatus = createReadStatus(ChannelType.PUBLIC);

        assertThat(readStatusService.find(privateStatus.getId())
                .notificationEnabled()).isTrue();

        assertThat(readStatusService.find(publicStatus.getId())
                .notificationEnabled()).isFalse();
    }

    @Test
    void 알림만_수정하면_읽음_시각은_유지된다() {
        ReadStatus status = createReadStatus(ChannelType.PUBLIC);
        Instant originalLastReadAt = status.getLastReadAt();

        ReadStatusDto enabled = readStatusService.update(
                status.getId(),
                new ReadStatusUpdateCommand(null, true)
        );

        assertThat(enabled.notificationEnabled()).isTrue();
        assertThat(enabled.lastReadAt()).isEqualTo(originalLastReadAt);

        ReadStatusDto disabled = readStatusService.update(
                status.getId(),
                new ReadStatusUpdateCommand(null, false)
        );

        assertThat(disabled.notificationEnabled()).isFalse();
        assertThat(disabled.lastReadAt()).isEqualTo(originalLastReadAt);
    }

    @Test
    void 읽음_시각만_수정하면_알림_설정은_유지된다() {
        ReadStatus status = createReadStatus(ChannelType.PRIVATE);
        Instant newLastReadAt = Instant.parse("2026-10-07T06:00:00Z");

        ReadStatusDto updated = readStatusService.update(
                status.getId(),
                new ReadStatusUpdateCommand(newLastReadAt, null)
        );

        assertThat(updated.lastReadAt()).isEqualTo(newLastReadAt);
        assertThat(updated.notificationEnabled()).isTrue();
    }



}

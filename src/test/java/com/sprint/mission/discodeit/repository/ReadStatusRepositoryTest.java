package com.sprint.mission.discodeit.repository;

import com.sprint.mission.discodeit.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReadStatusRepositoryTest {

    @Autowired
    private ReadStatusRepository readStatusRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Test
    void 해당_채널에서_알림을_켠_읽음상태만_조회한다() {
        Channel targetChannel = channelRepository.save(
                new Channel(ChannelType.PUBLIC, "general", "대상 채널")
        );
        Channel otherChannel = channelRepository.save(
                new Channel(ChannelType.PUBLIC, "other", "다른 채널")
        );

        ReadStatus enabled = createReadStatus(targetChannel, true);
        createReadStatus(targetChannel, false);
        createReadStatus(otherChannel, true);

        List<ReadStatus> result = readStatusRepository
                .findAllByChannelIdAndNotificationEnabledTrue(
                        targetChannel.getId()
                );

        assertThat(result)
                .extracting(ReadStatus::getId)
                .containsExactly(enabled.getId());
    }

    private ReadStatus createReadStatus(
            Channel channel,
            boolean notificationEnabled
    ) {
        String username = "user-" + UUID.randomUUID();

        User user = userRepository.save(
                new User(
                        username,
                        username + "@example.com",
                        "test-password",
                        Role.USER,
                        null
                )
        );

        ReadStatus readStatus = new ReadStatus(user, channel);
        readStatus.updateNotificationEnabled(notificationEnabled);

        return readStatusRepository.save(readStatus);


    }
}

package com.sprint.mission.discodeit.service;

import com.sprint.mission.discodeit.dto.channel.ChannelDto;
import com.sprint.mission.discodeit.dto.command.channel.PrivateChannelCommand;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.repository.ChannelRepository;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ChannelCacheIntegrationTest {

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReadStatusRepository readStatusRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 개인_채널_생성_후_참여자의_목록만_채널을_포함한다() {
        User participant = createUser();
        User outsider = createUser();
        UUID channelId = null;

        try {
            // 두 사용자의 기존 채널 목록을 각각 캐시에 저장한다.
            var participantBefore =
                    channelService.findAllByUserId(participant.getId());
            var outsiderBefore =
                    channelService.findAllByUserId(outsider.getId());

            ChannelDto created = channelService.createPrivateChannel(
                    new PrivateChannelCommand(List.of(participant.getId()))
            );
            channelId = created.id();

            // 참여자는 새 채널을 조회할 수 있어야 한다.
            var participantAfter =
                    channelService.findAllByUserId(participant.getId());

            assertThat(participantAfter)
                    .hasSize(participantBefore.size() + 1)
                    .extracting(ChannelDto::id)
                    .contains(created.id());

            // 비참여자는 해당 개인 채널을 조회할 수 없어야 한다.
            var outsiderAfter =
                    channelService.findAllByUserId(outsider.getId());

            assertThat(outsiderAfter)
                    .hasSize(outsiderBefore.size())
                    .extracting(ChannelDto::id)
                    .doesNotContain(created.id());

            // 캐시에 저장된 두 사용자 결과도 각각 유지되어야 한다.
            assertThat(channelService.findAllByUserId(participant.getId()))
                    .isEqualTo(participantAfter);
            assertThat(channelService.findAllByUserId(outsider.getId()))
                    .isEqualTo(outsiderAfter);
        } finally {
            UUID createdChannelId = channelId;

            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        if (createdChannelId != null) {
                            readStatusRepository.deleteByChannelId(
                                    createdChannelId
                            );
                            channelRepository.deleteById(createdChannelId);
                        }

                        userRepository.deleteById(participant.getId());
                        userRepository.deleteById(outsider.getId());
                    });

            cacheManager.getCache("channels").clear();
            cacheManager.getCache("users").clear();
        }
    }

    private User createUser() {
        String username = "channel-cache-" + UUID.randomUUID();

        return userRepository.save(new User(
                username,
                username + "@example.com",
                "test-password",
                Role.USER,
                null
        ));
    }


}

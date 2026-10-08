package com.sprint.mission.discodeit.event;

import java.util.UUID;

public record MessageCreatedEvent(
        UUID channelId,
        UUID senderId,
        String senderName,
        String channelName,
        String content
) {
}

package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.dto.message.MessageDto;

import java.util.UUID;

public record MessageCreatedEvent(
        UUID channelId,
        UUID senderId,
        String senderName,
        String channelName,
        String content,
        MessageDto messageDto
) {
}

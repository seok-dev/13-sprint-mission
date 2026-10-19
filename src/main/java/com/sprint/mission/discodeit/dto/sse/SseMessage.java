package com.sprint.mission.discodeit.dto.sse;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SseMessage(
        UUID id,
        String eventName,
        Object data,
        Set<UUID> receiverIds,
        boolean broadcast,
        Instant createdAt
) {

    public SseMessage {
        receiverIds = Set.copyOf(receiverIds);
    }

    public boolean canReceive(UUID receiverId) {
        return broadcast || receiverIds.contains(receiverId);
    }
}

package com.sprint.mission.discodeit.repository;

import com.sprint.mission.discodeit.dto.sse.SseMessage;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Repository
public class SseMessageRepository {

    private final ConcurrentLinkedDeque<UUID> eventIdQueue =
            new ConcurrentLinkedDeque<>();

    private final Map<UUID, SseMessage> messages =
            new ConcurrentHashMap<>();

    public synchronized void save(SseMessage message) {
        if (messages.putIfAbsent(message.id(), message) == null) {
            eventIdQueue.addLast(message.id());
        }
    }

    public synchronized List<SseMessage> findAllAfter(UUID receiverId, UUID lastEventId) {
        if (lastEventId == null) {
            return List.of();
        }

        List<SseMessage> result = new ArrayList<>();
        boolean found = false;

        for (UUID eventId : eventIdQueue) {
            if (!found) {
                if (eventId.equals(lastEventId)) {
                    found = true;
                }
                continue;
            }

            SseMessage message = messages.get(eventId);

            if (message != null && message.canReceive(receiverId)) {
                result.add(message);
            }
        }
        return List.copyOf(result);
    }

    public synchronized void deleteOlderThan(Instant cutoff) {
        var iterator = eventIdQueue.iterator();

        while (iterator.hasNext()) {
            UUID eventId = iterator.next();
            SseMessage message = messages.get(eventId);

            if (message == null || message.createdAt().isBefore(cutoff)) {
                messages.remove(eventId);
                iterator.remove();
            }
        }
    }

}

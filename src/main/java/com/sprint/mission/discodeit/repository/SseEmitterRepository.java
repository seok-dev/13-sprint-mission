package com.sprint.mission.discodeit.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class SseEmitterRepository {

    private final ConcurrentMap<UUID, List<SseEmitter>> data =
            new ConcurrentHashMap<>();

    public void save(UUID receiverId, SseEmitter emitter) {
        data.compute(receiverId, (id, emitters) -> {
            if (emitters == null) {
                emitters = new CopyOnWriteArrayList<>();
            }
            emitters.add(emitter);
            return emitters;
        });
    }

    public List<SseEmitter> findByReceiverId(UUID receiverId) {
        List<SseEmitter> emitters = data.get(receiverId);
        return emitters == null ? List.of() : List.copyOf(emitters);
    }

    public List<SseEmitter> findAll() {
        return data.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    public void delete(UUID receiverId, SseEmitter emitter) {
        data.computeIfPresent(receiverId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    public void delete(SseEmitter emitter) {
        for (UUID receiverId : data.keySet()) {
            delete(receiverId, emitter);
        }
    }

}

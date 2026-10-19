package com.sprint.mission.discodeit.repository;

import com.sprint.mission.discodeit.dto.sse.SseMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SseMessageRepositoryTest {

    private SseMessageRepository repository;

    private final UUID receiverId = UUID.randomUUID();
    private final UUID otherReceiverId = UUID.randomUUID();

    private final Instant baseTime =
            Instant.parse("2026-10-19T00:00:00Z");

    @BeforeEach
    void setUp() {
        repository = new SseMessageRepository();
    }

    @Test
    void 마지막_이벤트_이후의_수신_가능한_메시지만_저장_순서대로_조회한다() {
        SseMessage lastReceived = message(
                Set.of(receiverId), false, baseTime);
        SseMessage privateMessage = message(
                Set.of(receiverId), false, baseTime.plusSeconds(1));
        SseMessage otherMessage = message(
                Set.of(otherReceiverId), false, baseTime.plusSeconds(2));
        SseMessage broadcastMessage = message(
                Set.of(), true, baseTime.plusSeconds(3));


        repository.save(lastReceived);
        repository.save(privateMessage);
        repository.save(otherMessage);
        repository.save(broadcastMessage);

        assertThat(repository.findAllAfter(receiverId, lastReceived.id()))
                .containsExactly(privateMessage, broadcastMessage);
    }

    @Test
    void 같은_이벤트를_중복_저장하지_않는다() {
        SseMessage lastReceived = message(
                Set.of(receiverId), false, baseTime);
        SseMessage nextMessage = message(
                Set.of(receiverId), false, baseTime.plusSeconds(1));
        repository.save(lastReceived);
        repository.save(nextMessage);
        repository.save(nextMessage);

        assertThat(repository.findAllAfter(receiverId, lastReceived.id()))
                .containsExactly(nextMessage);
    }

    @Test
    void 마지막_이벤트_ID가_없거나_저장소에_없으면_빈_목록을_반환한다() {
        repository.save(message(Set.of(receiverId), false, baseTime));

        assertThat(repository.findAllAfter(receiverId, null)).isEmpty();
        assertThat(repository.findAllAfter(receiverId, UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void 기준_시각보다_오래된_메시지만_삭제한다() {
        Instant cutoff = baseTime.plusSeconds(10);

        SseMessage anchor = message(
                Set.of(receiverId), false, cutoff);
        SseMessage expired = message(
                Set.of(receiverId), false, cutoff.minusSeconds(1));
        SseMessage atCutoff = message(
                Set.of(receiverId), false, cutoff);
        SseMessage recent = message(
                Set.of(receiverId), false, cutoff.plusSeconds(1));

        // 저장 순서와 생성 시각이 달라도 만료 메시지를 찾아야 한다.
        repository.save(anchor);
        repository.save(expired);
        repository.save(atCutoff);
        repository.save(recent);

        repository.deleteOlderThan(cutoff);

        assertThat(repository.findAllAfter(receiverId, anchor.id()))
                .containsExactly(atCutoff, recent);

        // 삭제된 이벤트는 재전송 기준점으로도 찾을 수 없다.
        assertThat(repository.findAllAfter(receiverId, expired.id()))
                .isEmpty();
    }


    private SseMessage message(
            Set<UUID> receiverIds,
            boolean broadcast,
            Instant createdAt
    ){
        return new SseMessage(
                UUID.randomUUID(),
                "test.event",
                "테스트 데이터",
                receiverIds,
                broadcast,
                createdAt
        );
    }
}
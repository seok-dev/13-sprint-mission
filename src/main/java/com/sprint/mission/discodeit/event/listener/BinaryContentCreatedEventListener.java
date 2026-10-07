package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.entity.BinaryContentStatus;
import com.sprint.mission.discodeit.event.BinaryContentCreatedEvent;
import com.sprint.mission.discodeit.service.BinaryContentService;
import com.sprint.mission.discodeit.storage.BinaryContentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class BinaryContentCreatedEventListener {

    private final BinaryContentStorage binaryContentStorage;
    private final BinaryContentService binaryContentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(BinaryContentCreatedEvent event) {
        BinaryContentStatus status;

        try {
            binaryContentStorage.put(
                    event.binaryContentId(),
                    event.bytes()
            );
            status = BinaryContentStatus.SUCCESS;
        } catch (RuntimeException e) {
            log.error("파일 업로드 실패  - binaryContentId: {}", event.binaryContentId(), e);
            status = BinaryContentStatus.FAIL;
        }

        binaryContentService.updateStatus(event.binaryContentId(), status);
    }
}

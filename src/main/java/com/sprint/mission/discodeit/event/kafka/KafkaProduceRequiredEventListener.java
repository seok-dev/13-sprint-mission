package com.sprint.mission.discodeit.event.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.discodeit.event.MessageCreatedEvent;
import com.sprint.mission.discodeit.event.RoleUpdatedEvent;
import com.sprint.mission.discodeit.event.S3UploadFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "discodeit.kafka.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class KafkaProduceRequiredEventListener {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MessageCreatedEvent event) {
        publish("discodeit.MessageCreatedEvent", event);
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RoleUpdatedEvent event) {
        publish("discodeit.RoleUpdatedEvent", event);
    }

    @Async("taskExecutor")
    @EventListener
    public void on(S3UploadFailedEvent event) {
        publish("discodeit.S3UploadFailedEvent", event);
    }

    private  void publish(String topic, Object event) {
        String requestId = MDC.get("requestId");

        try {
            String payload = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(topic, payload)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.error("kafka 발행 실패 - topic: {}, requestId: {}", topic, requestId, exception);
                        } else {
                            log.info("kafka 발행 완료 - topic: {}, partition: {}, offset: {}, requestId: {}",
                                    topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset(),
                                    requestId
                            );
                        }
                    });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "kafka 이벤트 직렬화 실패 - topic: " + topic, e
            );
        }
    }
}

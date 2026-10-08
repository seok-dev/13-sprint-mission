package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.dto.binarycontent.BinaryContentDto;
import com.sprint.mission.discodeit.dto.command.binarycontent.BinaryContentCreateCommand;
import com.sprint.mission.discodeit.entity.BinaryContentStatus;
import com.sprint.mission.discodeit.repository.BinaryContentRepository;
import com.sprint.mission.discodeit.service.BinaryContentService;
import com.sprint.mission.discodeit.storage.BinaryContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class BinaryContentCreatedEventListenerTest {

    @Autowired
    private BinaryContentService binaryContentService;

    @Autowired
    private BinaryContentRepository  binaryContentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private BinaryContentStorage  binaryContentStorage;

    private TransactionTemplate transactionTemplate;
    private byte bytes[];
    private BinaryContentCreateCommand command;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        bytes = "hello".getBytes(StandardCharsets.UTF_8);
        command = new BinaryContentCreateCommand(
                "test.txt", bytes.length, "test/plain", bytes
        );
    }

    @Test
    void 커밋_후에만_업로드하고_SUCCESS를_저장한다() {
        BinaryContentDto  created = transactionTemplate.execute(status -> {
            BinaryContentDto dto = binaryContentService.create(command);

            assertThat(dto.status()).isEqualTo(BinaryContentStatus.PROCESSING);
            verifyNoInteractions(binaryContentStorage);
            return dto;
        });

        assertThat(created).isNotNull();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(()-> {
            BinaryContentDto saved = binaryContentService.find(created.id());

            assertThat(saved.status()).isEqualTo(BinaryContentStatus.SUCCESS);
        });

        verify(binaryContentStorage).put(created.id(), bytes);
    }

    @Test
    void 업로드가_실패하면_메타데이터를_유지하고_FAIL을_저장한다() {
        when(binaryContentStorage.put(any(UUID.class), any(byte[].class)))
                .thenThrow(new IllegalStateException("테스트용 업로드 실패"));

        BinaryContentDto created = transactionTemplate.execute(
                status -> binaryContentService.create(command)
        );

        assertThat(created).isNotNull();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(()-> {
            assertThat(binaryContentRepository.existsById(created.id())).isTrue();

            assertThat(binaryContentService.find(created.id()).status())
                    .isEqualTo(BinaryContentStatus.FAIL);
        });

        verify(binaryContentStorage).put(created.id(), bytes);
    }

    @Test
    void 롤백하면_업로드하지_않고_메타데이터도_남지_않는다() {
        BinaryContentDto created = transactionTemplate.execute(status -> {
            BinaryContentDto dto = binaryContentService.create(command);

            status.setRollbackOnly();
            return dto;
        });

        assertThat(created).isNotNull();
        verifyNoInteractions(binaryContentStorage);
        assertThat(binaryContentRepository.existsById(created.id())).isFalse();
    }







}
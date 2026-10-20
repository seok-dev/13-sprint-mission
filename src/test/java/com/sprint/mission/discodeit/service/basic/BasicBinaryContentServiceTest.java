package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.binarycontent.BinaryContentDto;
import com.sprint.mission.discodeit.entity.BinaryContent;
import com.sprint.mission.discodeit.entity.BinaryContentStatus;
import com.sprint.mission.discodeit.event.BinaryContentUpdatedEvent;
import com.sprint.mission.discodeit.mapper.BinaryContentMapper;
import com.sprint.mission.discodeit.repository.BinaryContentRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BasicBinaryContentServiceTest {

    @Mock
    private BinaryContentRepository binaryContentRepository;

    @Mock
    private BinaryContentMapper  binaryContentMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BasicBinaryContentService binaryContentService;

    @ParameterizedTest
    @EnumSource(
            value = BinaryContentStatus.class,
            names = {"SUCCESS", "FAIL"}
    )
    void 상태를_변경하고_변경된_DTO로_이벤트를_발행한다(
            BinaryContentStatus targetStatus
    ) {
        UUID binaryContentId = UUID.randomUUID();

        BinaryContent binaryContent = new BinaryContent(
                "테스트.png",
                100L,
                "image/png"
        );

        when(binaryContentRepository.findById(binaryContentId))
                .thenReturn(Optional.of(binaryContent));

        // 매퍼가 호출되는 시점의 엔티티 상태로 DTO를 만든다.
        when(binaryContentMapper.toDto(binaryContent))
                .thenAnswer(invocation -> new BinaryContentDto(
                        binaryContentId,
                        binaryContent.getFileName(),
                        binaryContent.getSize(),
                        binaryContent.getContentType(),
                        binaryContent.getStatus()
                ));

        BinaryContentDto result =
                binaryContentService.updateStatus(
                        binaryContentId,
                        targetStatus
                );

        assertThat(binaryContent.getStatus()).isEqualTo(targetStatus);
        assertThat(result.id()).isEqualTo(binaryContentId);
        assertThat(result.status()).isEqualTo(targetStatus);

        var order = inOrder(
                binaryContentRepository,
                binaryContentMapper,
                eventPublisher
        );

        order.verify(binaryContentRepository).findById(binaryContentId);
        order.verify(binaryContentMapper).toDto(binaryContent);
        order.verify(eventPublisher).publishEvent(
                new BinaryContentUpdatedEvent(result)
        );

        verifyNoMoreInteractions(eventPublisher);
    }









}
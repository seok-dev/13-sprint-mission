package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.binarycontent.BinaryContentDto;
import com.sprint.mission.discodeit.dto.command.binarycontent.BinaryContentCreateCommand;
import com.sprint.mission.discodeit.entity.BinaryContent;
import com.sprint.mission.discodeit.entity.BinaryContentStatus;
import com.sprint.mission.discodeit.event.BinaryContentCreatedEvent;
import com.sprint.mission.discodeit.exception.binarycontent.BinaryContentNotFoundException;
import com.sprint.mission.discodeit.mapper.BinaryContentMapper;
import com.sprint.mission.discodeit.repository.BinaryContentRepository;
import com.sprint.mission.discodeit.service.BinaryContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BasicBinaryContentService implements BinaryContentService {

    private final BinaryContentRepository binaryContentRepository;
    private final BinaryContentMapper binaryContentMapper;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    public BinaryContentDto create(BinaryContentCreateCommand command) {
        BinaryContent binaryContent = new BinaryContent(
                command.fileName(),
                command.fileSize(),
                command.contentType()
        );
        binaryContentRepository.save(binaryContent);
        eventPublisher.publishEvent(
                new BinaryContentCreatedEvent(
                        binaryContent.getId(),
                        command.bytes()
                )
        );
        log.info("파일 업로드 이벤트 발행 - Id: {}, fileName: {}, contentType: {}",
                binaryContent.getId(), binaryContent.getFileName(), binaryContent.getContentType());
        return binaryContentMapper.toDto(binaryContent);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BinaryContentDto updateStatus(UUID binaryContentId, BinaryContentStatus status) {
        BinaryContent binaryContent = binaryContentRepository.findById(binaryContentId)
                .orElseThrow(()-> BinaryContentNotFoundException.withId(binaryContentId));

        binaryContent.updateStatus(status);

        return binaryContentMapper.toDto(binaryContent);
    }

    @Override
    @Transactional(readOnly = true)
    public BinaryContentDto find(UUID id) {
        BinaryContent findByContent = binaryContentRepository
                .findById(id).orElseThrow(() -> BinaryContentNotFoundException.withId(id));
        return binaryContentMapper.toDto(findByContent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BinaryContentDto> findAllByIdIn(List<UUID> ids) {
        return ids.stream()
                .map(id ->
                        binaryContentMapper.toDto(binaryContentRepository.findById(id)
                                .orElseThrow(()-> BinaryContentNotFoundException.withId(id))))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(UUID id) {
        binaryContentRepository.findById(id)
                .orElseThrow(() -> BinaryContentNotFoundException.withId(id));
        binaryContentRepository.deleteById(id);
        log.info("파일 삭제 완료 - 파일Id: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public BinaryContent findEntity(UUID id) {
        return binaryContentRepository.findById(id)
                .orElseThrow(()-> BinaryContentNotFoundException.withId(id));
    }
}


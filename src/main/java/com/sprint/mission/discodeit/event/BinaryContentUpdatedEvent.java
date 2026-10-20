package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.dto.binarycontent.BinaryContentDto;

public record BinaryContentUpdatedEvent(
        BinaryContentDto binaryContent
) {
}

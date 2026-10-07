package com.sprint.mission.discodeit.dto.readstatus;

import com.sprint.mission.discodeit.dto.command.readstatus.ReadStatusUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ReadStatusUpdateRequest(
        @Schema(description = "수정할 읽음 상태 정보")
        Instant newLastReadAt,

        @Schema(description = "수정할 채널 알림 활성화 여부")
        Boolean newNotificationEnabled
)
{
    public ReadStatusUpdateCommand toCommand() {
        return new ReadStatusUpdateCommand(
                newLastReadAt,
                newNotificationEnabled
                );
    }
}

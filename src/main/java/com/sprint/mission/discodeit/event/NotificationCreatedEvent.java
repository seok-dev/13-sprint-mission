package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.dto.notification.NotificationDto;

public record NotificationCreatedEvent(
        NotificationDto notification
) {
}

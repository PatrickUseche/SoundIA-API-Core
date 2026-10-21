package com.soundia.queue.dto;

import java.time.LocalDateTime;

import com.soundia.queue.model.QueueItem;

public record QueueItemResponse(Long id, Long sessionId, Long trackId, LocalDateTime addedAt) {

    public static QueueItemResponse from(QueueItem item) {
        return new QueueItemResponse(item.getId(), item.getSessionId(), item.getTrackId(), item.getAddedAt());
    }
}

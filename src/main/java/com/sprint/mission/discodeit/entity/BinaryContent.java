package com.sprint.mission.discodeit.entity;

import com.sprint.mission.discodeit.entity.base.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.Getter;


@Getter
@Entity
@Table(name = "binary_contents")
public class BinaryContent extends BaseUpdatableEntity {

    @Column(nullable = false ,length = 255)
    private String fileName;

    @Column(nullable = false)
    private Long size;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BinaryContentStatus status = BinaryContentStatus.PROCESSING;

    protected BinaryContent() {
    }

    public BinaryContent(String fileName, Long size, String contentType) {
        super();
        this.fileName = fileName;
        this.size = size;
        this.contentType = contentType;
    }

    public void updateStatus(BinaryContentStatus status) {
        this.status = status;
    }
}
package org.ejectfb.ejectcloud.dto;

import java.time.Instant;

/**
 * DTO для отправки информации о файле на фронтенд.
 * Обычно формируется из сущности FileMeta.
 */
public class FileInfoDto {

    private Long id;
    private String filename;
    private long sizeBytes;
    private Instant uploadedAt;

    public FileInfoDto() {}

    public FileInfoDto(Long id, String filename, long sizeBytes, Instant uploadedAt) {
        this.id = id;
        this.filename = filename;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}

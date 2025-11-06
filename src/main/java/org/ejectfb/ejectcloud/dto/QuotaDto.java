package org.ejectfb.ejectcloud.dto;

/**
 * DTO для отображения или изменения квоты пользователя.
 * Используется в админ-панели.
 */
public class QuotaDto {

    private Long userId;
    private long quotaBytes;
    private long usedBytes;
    private long remainingBytes;

    public QuotaDto() {}

    public QuotaDto(Long userId, long quotaBytes, long usedBytes, long remainingBytes) {
        this.userId = userId;
        this.quotaBytes = quotaBytes;
        this.usedBytes = usedBytes;
        this.remainingBytes = remainingBytes;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public long getQuotaBytes() {
        return quotaBytes;
    }

    public void setQuotaBytes(long quotaBytes) {
        this.quotaBytes = quotaBytes;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public void setUsedBytes(long usedBytes) {
        this.usedBytes = usedBytes;
    }

    public long getRemainingBytes() {
        return remainingBytes;
    }

    public void setRemainingBytes(long remainingBytes) {
        this.remainingBytes = remainingBytes;
    }
}

package org.ejectfb.ejectcloud.dto;

/**
 * DTO для создания нового пользователя (используется админ-панелью
 * или Telegram ботом при одобрении пользователя).
 */
public class CreateUserDto {

    private String username;
    private long quotaBytes;
    private String displayName;

    public CreateUserDto() {}

    public CreateUserDto(String username, long quotaBytes, String displayName) {
        this.username = username;
        this.quotaBytes = quotaBytes;
        this.displayName = displayName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getQuotaBytes() {
        return quotaBytes;
    }

    public void setQuotaBytes(long quotaBytes) {
        this.quotaBytes = quotaBytes;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}

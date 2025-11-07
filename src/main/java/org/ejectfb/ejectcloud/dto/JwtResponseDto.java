package org.ejectfb.ejectcloud.dto;

public class JwtResponseDto {
    private String accessToken;
    private String refreshToken;
    private String telegramId;
    
    public JwtResponseDto() {}
    
    public JwtResponseDto(String accessToken, String refreshToken, String telegramId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.telegramId = telegramId;
    }
    
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    
    public String getTelegramId() { return telegramId; }
    public void setTelegramId(String telegramId) { this.telegramId = telegramId; }
}
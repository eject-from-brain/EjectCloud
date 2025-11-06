package org.ejectfb.ejectcloud.dto;

/**
 * DTO, возвращаемое при генерации временной ссылки авторизации.
 * Используется контроллером AuthController.
 */
public class LoginLinkDto {

    private String url;

    public LoginLinkDto() {}

    public LoginLinkDto(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

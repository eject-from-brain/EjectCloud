package org.ejectfb.ejectcloud;

import org.ejectfb.ejectcloud.service.UserService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "org.ejectfb.ejectcloud")
public class EjectCloudApplication {
    public static void main(String[] args) {
        // Проверяем наличие конфига
        File configFile = new File("config.properties");
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
            System.out.println("\n=== ВНИМАНИЕ ===");
            System.out.println("Создан файл config.properties");
            System.out.println("Заполните его настройками и перезапустите приложение.");
            System.out.println("================\n");
            System.exit(0);
        }
        
        // Устанавливаем путь к конфигу
        System.setProperty("spring.config.location", "file:./config.properties");
        
        try {
            SpringApplication.run(EjectCloudApplication.class, args);
        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createDefaultConfig(File configFile) {
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("# File upload settings\n");
            writer.write("spring.servlet.multipart.max-file-size=10GB\n");
            writer.write("spring.servlet.multipart.max-request-size=10GB\n");
            writer.write("spring.servlet.multipart.file-size-threshold=2KB\n");
            writer.write("spring.servlet.multipart.resolve-lazily=false\n");
            writer.write("\n");
            writer.write("# Base directory for file data (relative to working dir)\n");
            writer.write("ejectcloud.data-dir=./Data\n");
            writer.write("\n");
            writer.write("# Default user quota (1GB)\n");
            writer.write("ejectcloud.default.quota=1073741824\n");
            writer.write("\n");
            writer.write("# Share link expiration (hours)\n");
            writer.write("ejectcloud.share.expire-hours=24\n");
            writer.write("\n");
            writer.write("# JWT settings\n");
            writer.write("ejectcloud.jwt.access-token-minutes=15\n");
            writer.write("ejectcloud.jwt.refresh-token-days=30\n");
            writer.write("\n");
            writer.write("# Password security\n");
            writer.write("ejectcloud.password.salt=ejectcloud-secure-salt-change-in-production\n");
            writer.write("\n");
            writer.write("# Server settings - HTTPS\n");
            writer.write("server.port=8443\n");
            writer.write("server.ssl.enabled=true\n");
            writer.write("server.ssl.key-store=keystore.p12\n");
            writer.write("server.ssl.key-store-password=ejectcloud123\n");
            writer.write("server.ssl.key-store-type=PKCS12\n");
            writer.write("server.ssl.key-alias=ejectcloud\n");
            writer.write("server.tomcat.max-http-form-post-size=10GB\n");
            writer.write("server.tomcat.connection-timeout=10800000\n");
            writer.write("spring.mvc.async.request-timeout=10800000\n");
            writer.write("\n");
            writer.write("# Base URL for login links (change for production)\n");
            writer.write("ejectcloud.base-url=https://localhost:8443\n");
            writer.write("\n");
            writer.write("# Upload timeout (milliseconds)\n");
            writer.write("ejectcloud.upload.timeout=10800000\n");
            writer.write("\n");
            writer.write("# HTTP redirect port (redirects to HTTPS)\n");
            writer.write("ejectcloud.http.redirect-port=7443\n");
            writer.write("\n");
            writer.write("# DDoS Protection\n");
            writer.write("server.tomcat.max-connections=50\n");
            writer.write("server.tomcat.accept-count=10\n");
            writer.write("server.tomcat.max-threads=20\n");
            writer.write("server.compression.enabled=true\n");
            writer.write("server.compression.mime-types=text/html,text/xml,text/plain,text/css,text/javascript,application/javascript,application/json\n");
            writer.write("server.compression.min-response-size=1024\n");
            writer.write("\n");
            writer.write("# Security headers\n");
            writer.write("server.servlet.session.timeout=30m\n");
            writer.write("server.servlet.session.cookie.http-only=true\n");
            writer.write("server.servlet.session.cookie.secure=true\n");
        } catch (IOException e) {
            System.err.println("Ошибка создания конфига: " + e.getMessage());
            System.exit(1);
        }
    }
    
    @Component
    public static class StartupInitializer {
        private final UserService userService;
        
        public StartupInitializer(UserService userService) {
            this.userService = userService;
        }
        
        @EventListener(ApplicationReadyEvent.class)
        public void onApplicationReady() {
            userService.initializeDefaultAdmin();
        }
    }
    

}

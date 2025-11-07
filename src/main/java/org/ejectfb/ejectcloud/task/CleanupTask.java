package org.ejectfb.ejectcloud.task;

import org.ejectfb.ejectcloud.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CleanupTask {
    
    private final FileStorageService storageService;
    
    @Value("${ejectcloud.token.inactive.minutes:30}")
    private int inactiveMinutes;
    
    public CleanupTask(FileStorageService storageService) {
        this.storageService = storageService;
    }
    
    @Scheduled(fixedRate = 3600000) // каждый час
    public void cleanupExpiredShares() {
        storageService.cleanupExpiredShares();
    }
    
    @Scheduled(fixedRate = 300000) // каждые 5 минут
    public void cleanupInactiveTokens() {
        storageService.cleanupInactiveTokens(Duration.ofMinutes(inactiveMinutes));
    }
}
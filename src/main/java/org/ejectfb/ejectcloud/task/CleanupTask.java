package org.ejectfb.ejectcloud.task;

import org.ejectfb.ejectcloud.service.FileStorageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupTask {
    
    private final FileStorageService storageService;
    
    public CleanupTask(FileStorageService storageService) {
        this.storageService = storageService;
    }
    
    @Scheduled(fixedRate = 3600000) // каждый час
    public void cleanupExpiredShares() {
        storageService.cleanupExpiredShares();
    }
}
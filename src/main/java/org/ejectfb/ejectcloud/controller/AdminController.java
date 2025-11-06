package org.ejectfb.ejectcloud.controller;

import org.ejectfb.ejectcloud.model.UserData;
import org.ejectfb.ejectcloud.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/admin/api")
public class AdminController {
    private final FileStorageService storageService;
    
    @Value("${ejectcloud.data-dir:./Data}")
    private String dataDir;
    
    @Value("${ejectcloud.default.quota:1073741824}")
    private long defaultQuota;

    public AdminController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() {
        List<Map<String, Object>> users = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(Paths.get(dataDir))) {
            for (Path userDir : dirs) {
                if (Files.isDirectory(userDir)) {
                    String telegramId = userDir.getFileName().toString();
                    UserData userData = loadUserData(telegramId);
                    if (userData != null) {
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("telegramId", userData.getTelegramId());
                        userMap.put("username", userData.getUsername());
                        userMap.put("quotaBytes", userData.getQuotaBytes());
                        userMap.put("usedBytes", storageService.calculateUsedBytes(telegramId));
                        userMap.put("createdAt", userData.getCreatedAt());
                        users.add(userMap);
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return users;
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestParam String telegramId, 
                                       @RequestParam String username,
                                       @RequestParam(required = false) Long quotaBytes) {
        long quota = quotaBytes != null ? quotaBytes : defaultQuota;
        UserData userData = storageService.getOrCreateUser(telegramId, username, quota);
        return ResponseEntity.ok(userData);
    }

    private UserData loadUserData(String telegramId) {
        try {
            return storageService.getOrCreateUser(telegramId, "user_" + telegramId, defaultQuota);
        } catch (Exception e) {
            return null;
        }
    }
}
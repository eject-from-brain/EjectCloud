package org.ejectfb.ejectcloud.controller;

import org.ejectfb.ejectcloud.model.SystemStats;
import org.ejectfb.ejectcloud.model.UserData;
import org.ejectfb.ejectcloud.service.FileStorageService;
import org.ejectfb.ejectcloud.service.SystemMonitorService;
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
    private final SystemMonitorService monitorService;
    
    @Value("${ejectcloud.data-dir:./Data}")
    private String dataDir;
    
    @Value("${ejectcloud.default.quota:1073741824}")
    private long defaultQuota;

    public AdminController(FileStorageService storageService, SystemMonitorService monitorService) {
        this.storageService = storageService;
        this.monitorService = monitorService;
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@RequestParam(required = false) String token) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(getUsersList());
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestParam(required = false) String token,
                                       @RequestParam String telegramId, 
                                       @RequestParam String username,
                                       @RequestParam(required = false) Long quotaBytes) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        long quota = quotaBytes != null ? quotaBytes : defaultQuota;
        UserData userData = storageService.getOrCreateUser(telegramId, username, quota);
        return ResponseEntity.ok(userData);
    }

    @PostMapping("/quota")
    public ResponseEntity<?> updateQuota(@RequestParam(required = false) String token,
                                        @RequestParam String telegramId, 
                                        @RequestParam long quotaBytes) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            UserData userData = loadUserData(telegramId);
            if (userData != null) {
                userData.setQuotaBytes(quotaBytes);
                storageService.saveUserData(telegramId, userData);
                return ResponseEntity.ok(Map.of("success", true));
            }
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/system/current")
    public ResponseEntity<?> getCurrentSystemStats(@RequestParam(required = false) String token) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(monitorService.getCurrentStats());
    }
    
    @GetMapping("/system/history")
    public ResponseEntity<?> getSystemHistory(@RequestParam(required = false) String token,
                                             @RequestParam(defaultValue = "1h") String period) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(monitorService.getFilteredStats(period));
    }
    
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@RequestParam(required = false) String token) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        Map<String, Object> dashboard = new HashMap<>();
        
        // Системная информация
        SystemStats.StatsEntry current = monitorService.getCurrentStats();
        dashboard.put("system", current);
        
        // Статистика пользователей
        List<Map<String, Object>> users = getUsersList();
        long totalUsed = users.stream().mapToLong(u -> (Long) u.get("usedBytes")).sum();
        long totalQuota = users.stream().mapToLong(u -> (Long) u.get("quotaBytes")).sum();
        
        dashboard.put("users", Map.of(
            "count", users.size(),
            "totalUsed", totalUsed,
            "totalQuota", totalQuota,
            "list", users
        ));
        
        return ResponseEntity.ok(dashboard);
    }
    
    private boolean isValidAdminToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        if (!storageService.isValidToken(token)) {
            return false;
        }
        
        // Проверяем, что токен принадлежит админу
        String telegramId = storageService.getTelegramIdByToken(token);
        return isAdmin(telegramId);
    }
    
    @Value("${telegram.admin.chatid:}")
    private String adminChatId;
    
    private boolean isAdmin(String telegramId) {
        return adminChatId != null && adminChatId.equals(telegramId);
    }

    private List<Map<String, Object>> getUsersList() {
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

    @PostMapping("/users/delete")
    public ResponseEntity<?> deleteUser(@RequestParam(required = false) String token,
                                       @RequestParam String telegramId) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            Path userDir = Paths.get(dataDir, telegramId);
            if (Files.exists(userDir)) {
                Files.walk(userDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private UserData loadUserData(String telegramId) {
        try {
            return storageService.getOrCreateUser(telegramId, "user_" + telegramId, defaultQuota);
        } catch (Exception e) {
            return null;
        }
    }
}
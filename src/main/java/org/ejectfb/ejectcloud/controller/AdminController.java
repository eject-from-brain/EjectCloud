package org.ejectfb.ejectcloud.controller;

import org.ejectfb.ejectcloud.model.SystemStats;
import org.ejectfb.ejectcloud.model.UserData;
import org.ejectfb.ejectcloud.service.FileStorageService;
import org.ejectfb.ejectcloud.service.JwtService;
import org.ejectfb.ejectcloud.service.SystemMonitorService;
import org.ejectfb.ejectcloud.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/api")
public class AdminController {
    
    private final UserService userService;
    private final JwtService jwtService;
    private final FileStorageService storageService;
    private final SystemMonitorService monitorService;

    public AdminController(UserService userService, JwtService jwtService, 
                          FileStorageService storageService, SystemMonitorService monitorService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.storageService = storageService;
        this.monitorService = monitorService;
    }

    private boolean isValidAdminToken(String token) {
        String telegramId = jwtService.validateAccessToken(token);
        if (telegramId != null) {
            UserData user = userService.findUserByTelegramId(telegramId);
            return user != null && user.isAdmin();
        }
        return false;
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@RequestParam String token) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        List<Map<String, Object>> users = new ArrayList<>();
        for (UserData user : userService.getAllUsers()) {
            Map<String, Object> userMap = new HashMap<>();
            long usedBytes = storageService.calculateUsedBytes(user.getId());
            userMap.put("id", user.getId());
            userMap.put("email", user.getEmail());
            userMap.put("displayName", user.getDisplayName());
            userMap.put("telegramId", user.getTelegramId());
            userMap.put("quotaBytes", user.getQuotaBytes());
            userMap.put("usedBytes", usedBytes);
            userMap.put("usagePercent", user.getQuotaBytes() > 0 ? (double) usedBytes / user.getQuotaBytes() * 100 : 0);
            userMap.put("createdAt", user.getCreatedAt());
            userMap.put("isAdmin", user.isAdmin());
            users.add(userMap);
        }
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestParam String token,
                                       @RequestParam String email,
                                       @RequestParam String displayName,
                                       @RequestParam String telegramId,
                                       @RequestParam(required = false) Long quotaBytes) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        if (userService.findUserByEmail(email) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User already exists"));
        }
        
        long quota = quotaBytes != null ? quotaBytes : 1073741824L;
        UserData userData = userService.createUser(email, email, displayName, telegramId, false);
        userData.setQuotaBytes(quota);
        userService.saveUserData(userData.getId(), userData);
        
        return ResponseEntity.ok(userData);
    }

    @PostMapping("/quota")
    public ResponseEntity<?> updateQuota(@RequestParam String token,
                                        @RequestParam String userId,
                                        @RequestParam long quotaBytes) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        UserData userData = userService.loadUserData(userId);
        if (userData != null) {
            userData.setQuotaBytes(quotaBytes);
            userService.saveUserData(userId, userData);
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
    }
    
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String token,
                                          @RequestParam String userId) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        userService.resetPassword(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }
    
    @PostMapping("/users/edit")
    public ResponseEntity<?> editUser(@RequestParam String token,
                                     @RequestParam String userId,
                                     @RequestParam String email,
                                     @RequestParam String displayName,
                                     @RequestParam String telegramId,
                                     @RequestParam long quotaBytes) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        userService.updateUser(userId, email, displayName, telegramId, quotaBytes);
        return ResponseEntity.ok(Map.of("success", true));
    }
    
    @PostMapping("/users/delete")
    public ResponseEntity<?> deleteUser(@RequestParam String token,
                                       @RequestParam String userId) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        userService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }
    
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@RequestParam String token) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        Map<String, Object> dashboard = new HashMap<>();
        
        SystemStats.StatsEntry current = monitorService.getCurrentStats();
        dashboard.put("system", current);
        
        List<UserData> users = userService.getAllUsers();
        long totalUsed = users.stream().mapToLong(u -> storageService.calculateUsedBytes(u.getId())).sum();
        long totalQuota = users.stream().mapToLong(UserData::getQuotaBytes).sum();
        
        // Подготавливаем список пользователей с данными об использовании
        List<Map<String, Object>> usersList = new ArrayList<>();
        for (UserData user : users) {
            Map<String, Object> userMap = new HashMap<>();
            long usedBytes = storageService.calculateUsedBytes(user.getId());
            userMap.put("username", user.getDisplayName());
            userMap.put("usedBytes", usedBytes);
            userMap.put("quotaBytes", user.getQuotaBytes());
            usersList.add(userMap);
        }
        
        dashboard.put("users", Map.of(
            "count", users.size(),
            "totalUsed", totalUsed,
            "totalQuota", totalQuota,
            "list", usersList
        ));
        
        return ResponseEntity.ok(dashboard);
    }
    
    @GetMapping("/system/history")
    public ResponseEntity<?> getSystemHistory(@RequestParam String token,
                                             @RequestParam(defaultValue = "1h") String period) {
        if (!isValidAdminToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(monitorService.getFilteredStats(period));
    }
}
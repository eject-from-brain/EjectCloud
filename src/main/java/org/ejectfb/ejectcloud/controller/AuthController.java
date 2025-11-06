package org.ejectfb.ejectcloud.controller;

import org.ejectfb.ejectcloud.service.FileStorageService;
import org.ejectfb.ejectcloud.dto.LoginLinkDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final FileStorageService storageService;
    
    @Value("${ejectcloud.base-url:http://localhost:8080}")
    private String baseUrl;

    public AuthController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validate(@RequestParam String token) {
        if (storageService.isValidToken(token)) {
            storageService.touchToken(token);
            String telegramId = storageService.getTelegramIdByToken(token);
            return ResponseEntity.ok(java.util.Map.of("ok", true, "user", telegramId));
        }
        return ResponseEntity.status(401).body(java.util.Map.of("ok", false));
    }
}
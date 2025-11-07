package org.ejectfb.ejectcloud.controller;

import org.ejectfb.ejectcloud.model.UserData;
import org.ejectfb.ejectcloud.service.JwtService;
import org.ejectfb.ejectcloud.service.UserService;
import org.ejectfb.ejectcloud.dto.JwtResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String email, @RequestParam String password) {
        UserData user = userService.authenticate(email, password);
        if (user != null) {
            String accessToken = jwtService.generateAccessToken(user.getTelegramId());
            String refreshToken = jwtService.generateRefreshToken(user.getTelegramId());
            
            return ResponseEntity.ok(new JwtResponseDto(accessToken, refreshToken, user.getTelegramId()));
        }
        
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }
    
    @GetMapping("/validate")
    public ResponseEntity<?> validate(@RequestParam String token) {
        String telegramId = jwtService.validateAccessToken(token);
        if (telegramId != null) {
            UserData user = userService.findUserByTelegramId(telegramId);
            if (user != null) {
                return ResponseEntity.ok(Map.of(
                    "ok", true, 
                    "user", user.getDisplayName(),
                    "userId", user.getId(),
                    "telegramId", telegramId,
                    "isAdmin", user.isAdmin(),
                    "mustChangePassword", user.isMustChangePassword()
                ));
            }
        }
        
        return ResponseEntity.status(401).body(Map.of("ok", false));
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestParam String refreshToken) {
        String telegramId = jwtService.validateRefreshToken(refreshToken);
        if (telegramId != null) {
            String newAccessToken = jwtService.generateAccessToken(telegramId);
            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
        }
        
        return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        jwtService.revokeRefreshToken(refreshToken);
        return ResponseEntity.ok(Map.of("ok", true));
    }
    
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestParam String token,
                                          @RequestParam String currentPassword,
                                          @RequestParam String newPassword) {
        String telegramId = jwtService.validateAccessToken(token);
        if (telegramId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
        
        UserData user = userService.findUserByTelegramId(telegramId);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        
        UserData authUser = userService.authenticate(user.getEmail(), currentPassword);
        if (authUser == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Current password is incorrect"));
        }
        
        userService.updatePassword(user.getId(), newPassword);
        return ResponseEntity.ok(Map.of("success", true));
    }
    
    @PostMapping("/update-profile")
    public ResponseEntity<?> updateProfile(@RequestParam String token,
                                         @RequestParam String email,
                                         @RequestParam String displayName) {
        String telegramId = jwtService.validateAccessToken(token);
        if (telegramId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
        
        UserData user = userService.findUserByTelegramId(telegramId);
        if (user != null) {
            userService.updateProfile(user.getId(), email, displayName);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
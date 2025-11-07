package org.ejectfb.ejectcloud.controller;

import org.ejectfb.ejectcloud.model.FileData;
import org.ejectfb.ejectcloud.model.UserData;
import org.ejectfb.ejectcloud.service.FileStorageService;
import org.ejectfb.ejectcloud.service.JwtService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileStorageService storageService;
    private final JwtService jwtService;

    public FileController(FileStorageService storageService, JwtService jwtService) {
        this.storageService = storageService;
        this.jwtService = jwtService;
    }

    private String requireUserId(String token) {
        String telegramId = jwtService.validateAccessToken(token);
        if (telegramId != null) {
            UserData user = storageService.getUserService().findUserByTelegramId(telegramId);
            if (user != null) {
                return user.getId();
            }
        }
        throw new RuntimeException("Invalid token");
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam String token, 
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String path) {
        String userId = requireUserId(token);
        try {
            String originalName = file.getOriginalFilename();
            FileData fileData = storageService.uploadFile(userId, file, path);
            
            if (!fileData.getFilename().equals(originalName)) {
                return ResponseEntity.ok(java.util.Map.of(
                    "file", fileData,
                    "renamed", true,
                    "originalName", originalName,
                    "newName", fileData.getFilename()
                ));
            }
            
            return ResponseEntity.ok(fileData);
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("Недостаточно места")) {
                return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE)
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Ошибка сохранения файла");
        }
    }
    
    @PostMapping("/mkdir")
    public ResponseEntity<?> createDirectory(@RequestParam String token, @RequestParam String path) {
        String userId = requireUserId(token);
        try {
            storageService.createDirectory(userId, path);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
    
    @GetMapping("/folders")
    public ResponseEntity<?> getFolders(@RequestParam String token) {
        String userId = requireUserId(token);
        return ResponseEntity.ok(storageService.listFolders(userId));
    }
    
    @GetMapping("/trash")
    public ResponseEntity<?> getTrash(@RequestParam String token) {
        String userId = requireUserId(token);
        return ResponseEntity.ok(storageService.listTrash(userId));
    }
    
    @GetMapping("/trash/folders")
    public ResponseEntity<?> getTrashFolders(@RequestParam String token) {
        String userId = requireUserId(token);
        return ResponseEntity.ok(storageService.listTrashFolders(userId));
    }
    
    @DeleteMapping("/trash/clear")
    public ResponseEntity<?> clearTrash(@RequestParam String token) {
        String userId = requireUserId(token);
        try {
            storageService.clearTrash(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
    
    @DeleteMapping("/trash/{id}")
    public ResponseEntity<?> deleteFromTrash(@PathVariable String id, @RequestParam String token) {
        String userId = requireUserId(token);
        try {
            storageService.deleteFromTrash(userId, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
    
    @PostMapping("/trash/restore/{id}")
    public ResponseEntity<?> restoreFromTrash(@PathVariable String id, @RequestParam String token) {
        String userId = requireUserId(token);
        try {
            storageService.restoreFromTrash(userId, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
    
    @GetMapping("/quota")
    public ResponseEntity<?> getQuotaInfo(@RequestParam String token) {
        String userId = requireUserId(token);
        
        UserData userData = storageService.getOrCreateUser(userId, "user_" + userId, 1073741824L);
        long totalUsed = storageService.calculateTotalUsedBytes(userId);
        long quota = userData.getQuotaBytes();
        
        return ResponseEntity.ok(java.util.Map.of(
            "used", totalUsed,
            "quota", quota,
            "remaining", Math.max(0, quota - totalUsed),
            "percentage", quota > 0 ? (double) totalUsed / quota * 100 : 0
        ));
    }

    @GetMapping("/list")
    public List<?> list(@RequestParam String token) {
        String userId = requireUserId(token);
        return storageService.listFiles(userId)
                .stream()
                .map(f -> {
                    java.util.Map<String, Object> fileMap = new java.util.HashMap<>();
                    fileMap.put("id", f.getId());
                    fileMap.put("filename", f.getFilename());
                    fileMap.put("size", f.getSizeBytes());
                    fileMap.put("uploadedAt", f.getUploadedAt());
                    fileMap.put("shared", f.isShared());
                    if (f.isShared()) {
                        fileMap.put("shareExpiresAt", f.getShareExpiresAt());
                    }
                    return fileMap;
                }).collect(Collectors.toList());
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> download(@PathVariable String id, @RequestParam String token) {
        String userId = requireUserId(token);
        
        FileData fileData = storageService.listFiles(userId)
                .stream()
                .filter(f -> f.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found"));
        
        Path filePath = storageService.getFilePath(userId, id);
        try {
            InputStreamResource resource = new InputStreamResource(new FileInputStream(filePath.toFile()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileData.getFilename() + "\"")
                    .contentLength(fileData.getSizeBytes())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Cannot read file");
        }
    }

    @PostMapping("/share/{id}")
    public ResponseEntity<?> createShare(@PathVariable String id, @RequestParam String token) {
        String userId = requireUserId(token);
        try {
            String shareId = storageService.createShare(userId, id);
            String shareUrl = "/share/" + shareId;
            return ResponseEntity.ok(java.util.Map.of("shareUrl", shareUrl));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
    
    @DeleteMapping("/share/{id}")
    public ResponseEntity<?> deleteShare(@PathVariable String id, @RequestParam String token) {
        String userId = requireUserId(token);
        try {
            storageService.deleteShare(userId, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
    
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFile(@RequestParam String id, @RequestParam String token) {
        String userId = requireUserId(token);
        try {
            storageService.moveToTrash(userId, id, false);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
    
    @DeleteMapping("/folder")
    public ResponseEntity<?> deleteFolder(@RequestParam String path, @RequestParam String token) {
        String userId = requireUserId(token);
        try {
            storageService.moveToTrash(userId, path, true);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
    
    @PostMapping("/move")
    public ResponseEntity<?> moveFile(@RequestParam String fileId, 
                                     @RequestParam String targetFolder, 
                                     @RequestParam String token) {
        String userId = requireUserId(token);
        try {
            storageService.moveFile(userId, fileId, targetFolder);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}
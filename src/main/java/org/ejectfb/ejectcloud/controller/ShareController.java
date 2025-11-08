package org.ejectfb.ejectcloud.controller;

import org.ejectfb.ejectcloud.model.FileData;
import org.ejectfb.ejectcloud.service.FileStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Path;

@RestController
@RequestMapping("/share")
public class ShareController {
    private final FileStorageService storageService;

    public ShareController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/{shareId}")
    public ResponseEntity<?> downloadShared(@PathVariable String shareId) {
        try {
            FileData fileData = storageService.getFileByShare(shareId);
            if (fileData == null) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = storageService.getFilePath(fileData.getTelegramId(), fileData.getId());

            if (!java.nio.file.Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(filePath.toFile()));
            
            // Правильное кодирование имени файла для UTF-8
            String encodedFilename = java.net.URLEncoder.encode(fileData.getFilename(), "UTF-8")
                    .replaceAll("\\+", "%20");
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename*=UTF-8''" + encodedFilename)
                    .contentLength(fileData.getSizeBytes())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка скачивания файла: " + e.getMessage());
        }
    }
}
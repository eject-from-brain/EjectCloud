package org.ejectfb.ejectcloud.controller;

import org.ejectfb.ejectcloud.model.FileData;
import org.ejectfb.ejectcloud.service.FileStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.*;
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
        System.out.println("Share request for ID: " + shareId);
        
        FileData fileData = storageService.getFileByShare(shareId);
        if (fileData == null) {
            System.out.println("File data not found for share ID: " + shareId);
            return ResponseEntity.notFound().build();
        }

        System.out.println("Found file: " + fileData.getFilename() + " for user: " + fileData.getTelegramId());
        Path filePath = storageService.getFilePath(fileData.getTelegramId(), fileData.getId());
        System.out.println("File path: " + filePath);
        
        if (!java.nio.file.Files.exists(filePath)) {
            System.out.println("File does not exist at path: " + filePath);
            return ResponseEntity.notFound().build();
        }

        try {
            InputStreamResource resource = new InputStreamResource(new FileInputStream(filePath.toFile()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileData.getFilename() + "\"")
                    .contentLength(fileData.getSizeBytes())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            return ResponseEntity.status(500).body("Cannot read file");
        }
    }
}
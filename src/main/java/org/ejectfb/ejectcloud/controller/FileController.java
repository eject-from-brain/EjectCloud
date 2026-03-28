package org.ejectfb.ejectcloud.controller;

import org.ejectfb.ejectcloud.model.FileData;
import org.ejectfb.ejectcloud.model.UserData;
import org.ejectfb.ejectcloud.service.FileStorageService;
import org.ejectfb.ejectcloud.service.ArchiveService;
import org.ejectfb.ejectcloud.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private final FileStorageService storageService;
    private final JwtService jwtService;
    private final ArchiveService archiveService;
    
    @Value("${ejectcloud.upload.timeout:10800000}")
    private long uploadTimeout;

    public FileController(FileStorageService storageService, JwtService jwtService, ArchiveService archiveService) {
        this.storageService = storageService;
        this.jwtService = jwtService;
        this.archiveService = archiveService;
    }

    private String requireUserId(String token) {
        try {
            String telegramId = jwtService.validateAccessToken(token);
            if (telegramId != null) {
                UserData user = storageService.getUserService().findUserByTelegramId(telegramId);
                if (user != null) {
                    return user.getId();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Недействительный токен");
        }
        throw new RuntimeException("Недействительный токен");
    }

    private Path safeResolveUserFile(String userId, String fileId) {
        if (fileId == null) {
            throw new IllegalStateException("fileId is required");
        }

        if (fileId.contains("..") || fileId.contains("\\") || fileId.startsWith("/") || fileId.startsWith("\\")) {
            throw new IllegalStateException("Invalid fileId");
        }

        Path dataDir = storageService.getDataDir(userId).toAbsolutePath().normalize();
        Path p;
        try {
            p = dataDir.resolve(fileId).normalize();
        } catch (InvalidPathException e) {
            throw new IllegalStateException("Invalid fileId");
        }
        if (!p.startsWith(dataDir)) {
            throw new IllegalStateException("Invalid fileId");
        }
        return p;
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
    
    @GetMapping("/config/upload-timeout")
    public ResponseEntity<?> getUploadTimeout() {
        return ResponseEntity.ok(java.util.Map.of(
            "timeout", uploadTimeout
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

    @GetMapping("/download")
    public ResponseEntity<?> download(@RequestParam String fileId, @RequestParam String token) {
        try {
            String userId = requireUserId(token);
            
            FileData fileData = storageService.listFiles(userId)
                    .stream()
                    .filter(f -> f.getId().equals(fileId))
                    .findFirst()
                    .orElse(null);
            
            if (fileData == null) {
                return ResponseEntity.notFound().build();
            }
            
            Path filePath = safeResolveUserFile(userId, fileId);
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Ошибка скачивания файла: " + e.getMessage());
        }
    }

    @GetMapping("/info")
    public ResponseEntity<?> fileInfo(@RequestParam String fileId, @RequestParam String token) {
        try {
            String userId = requireUserId(token);
            FileData fileData = storageService.listFiles(userId)
                .stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElse(null);

            if (fileData == null) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = safeResolveUserFile(userId, fileId);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = MediaTypeFactory.getMediaType(filePath.getFileName().toString())
                    .map(MediaType::toString)
                    .orElse("application/octet-stream");
            }

            return ResponseEntity.ok(java.util.Map.of(
                "id", fileData.getId(),
                "filename", fileData.getFilename(),
                "size", fileData.getSizeBytes(),
                "uploadedAt", fileData.getUploadedAt(),
                "contentType", contentType
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/view")
    public ResponseEntity<?> viewInline(@RequestParam String fileId, @RequestParam String token, @RequestHeader HttpHeaders headers) {
        try {
            String userId = requireUserId(token);
            Path filePath = safeResolveUserFile(userId, fileId);
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath);
            long len = Files.size(filePath);
            MediaType mt = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);

            // Avoid executing HTML/SVG/etc in preview: if looks like HTML/SVG, force octet-stream.
            String mtStr = mt.toString();
            if (mtStr.startsWith("text/html") || mtStr.contains("svg") || mtStr.contains("xml")) {
                mt = MediaType.APPLICATION_OCTET_STREAM;
            }

            String filename = filePath.getFileName().toString();
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            // Range support (important for video)
            List<HttpRange> ranges = headers.getRange();
            if (ranges != null && !ranges.isEmpty()) {
                HttpRange range = ranges.get(0);
                long start = range.getRangeStart(len);
                long end = range.getRangeEnd(len);
                long rangeLen = Math.min(1 + end - start, len);
                org.springframework.core.io.support.ResourceRegion region = new org.springframework.core.io.support.ResourceRegion(resource, start, rangeLen);

                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFilename)
                    .header("Accept-Ranges", "bytes")
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(mt)
                    .body(region);
            }

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFilename)
                .header("Accept-Ranges", "bytes")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mt)
                .contentLength(len)
                .body(resource);
        } catch (Exception e) {
            log.warn("[view] inline error fileId='{}' msg={}", fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/text")
    public ResponseEntity<?> viewText(@RequestParam String fileId, @RequestParam String token) {
        try {
            String userId = requireUserId(token);
            Path filePath = safeResolveUserFile(userId, fileId);
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return ResponseEntity.notFound().build();
            }

            long len = Files.size(filePath);
            long max = 2L * 1024L * 1024L; // 2MB
            if (len > max) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body("File is too large for preview");
            }

            byte[] bytes = Files.readAllBytes(filePath);

            // Basic binary sniffing: reject if contains NUL or too few printable chars
            if (!isProbablyText(bytes)) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body("Binary file preview is not supported");
            }

            String text = decodeText(bytes);

            return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
                .body(text);
        } catch (Exception e) {
            log.warn("[view] text error fileId='{}' msg={}", fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body("Error: " + e.getMessage());
        }
    }

    private static boolean isProbablyText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return true;
        int n = Math.min(bytes.length, 64 * 1024);
        int control = 0;
        for (int i = 0; i < n; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0x00) return false;
            // allowed whitespace controls
            if (b == 0x09 || b == 0x0A || b == 0x0D) {
                continue;
            }
            // count other control chars as binary-ish
            if (b < 0x20 || b == 0x7F) {
                control++;
            }
        }
        double controlRatio = (double) control / (double) n;
        return controlRatio <= 0.02;
    }

    private static String decodeText(byte[] bytes) {
        if (bytes.length >= 2) {
            // UTF-16 BOM
            if ((bytes[0] == (byte)0xFF && bytes[1] == (byte)0xFE)) {
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_16LE);
            }
            if ((bytes[0] == (byte)0xFE && bytes[1] == (byte)0xFF)) {
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_16BE);
            }
        }

        if (bytes.length >= 3) {
            // UTF-8 BOM
            if ((bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF)) {
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        try {
            var dec = java.nio.charset.StandardCharsets.UTF_8.newDecoder();
            dec.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
            dec.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            return dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (Exception ignored) {
            // fallback: common Windows Cyrillic encoding
            try {
                return new String(bytes, java.nio.charset.Charset.forName("windows-1251"));
            } catch (Exception ignored2) {
                return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            }
        }
    }

    @PostMapping("/share")
    public ResponseEntity<?> createShare(@RequestParam String fileId, @RequestParam String token) {
        try {
            String userId = requireUserId(token);
            String shareId = storageService.createShare(userId, fileId);
            String shareUrl = "/share/" + shareId;
            return ResponseEntity.ok(java.util.Map.of("shareUrl", shareUrl));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(java.util.Map.of("error", "Ошибка создания ссылки: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/share")
    public ResponseEntity<?> deleteShare(@RequestParam String fileId, @RequestParam String token) {
        try {
            String userId = requireUserId(token);
            storageService.deleteShare(userId, fileId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(java.util.Map.of("error", e.getMessage()));
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
        try {
            String userId = requireUserId(token);
            String newFileName = storageService.moveFile(userId, fileId, targetFolder);
            
            if (newFileName != null) {
                return ResponseEntity.ok(java.util.Map.of(
                    "renamed", true,
                    "newName", newFileName
                ));
            }
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/rename")
    public ResponseEntity<?> renameFile(@RequestParam String fileId, 
                                       @RequestParam String newName, 
                                       @RequestParam String token) {
        try {
            String userId = requireUserId(token);
            storageService.renameFile(userId, fileId, newName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/rename-folder")
    public ResponseEntity<?> renameFolder(@RequestParam String folderPath, 
                                         @RequestParam String newName, 
                                         @RequestParam String token) {
        try {
            String userId = requireUserId(token);
            storageService.renameFolder(userId, folderPath, newName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/archive")
    public ResponseEntity<?> startArchive(@RequestParam String token,
                                         @RequestParam(required = false, defaultValue = "") String path,
                                         @RequestParam(required = false) String fileName) {
        try {
            String userId = requireUserId(token);
            String safePath = URLEncoder.encode(path == null ? "" : path, StandardCharsets.UTF_8);
            String safeName = URLEncoder.encode(fileName == null ? "" : fileName, StandardCharsets.UTF_8);
            log.info("[archive] request start userId={} pathEnc='{}' fileNameEnc='{}'", userId, safePath, safeName);
            String jobId = archiveService.startJob(userId, path, fileName);
            return ResponseEntity.ok(java.util.Map.of("jobId", jobId));
        } catch (Exception e) {
            log.warn("[archive] request start error path='{}' fileName='{}' msg={}", path, fileName, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/archive/status")
    public ResponseEntity<?> archiveStatus(@RequestParam String token, @RequestParam String jobId) {
        try {
            String userId = requireUserId(token);
            log.debug("[archive] status userId={} jobId={}", userId, jobId);
            return ResponseEntity.ok(archiveService.getStatus(userId, jobId));
        } catch (Exception e) {
            log.debug("[archive] status error jobId={} msg={}", jobId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping(value = "/archive/download", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> downloadArchive(@RequestParam String token, @RequestParam String jobId) {
        try {
            String userId = requireUserId(token);
            Path zipPath = archiveService.getZipPathForDownload(userId, jobId);
            if (!java.nio.file.Files.exists(zipPath)) {
                return ResponseEntity.notFound().build();
            }

            String name = archiveService.getFileName(userId, jobId);
            String encodedFilename = java.net.URLEncoder.encode(name, "UTF-8").replaceAll("\\+", "%20");

            long len = java.nio.file.Files.size(zipPath);
            log.info("[archive] download start userId={} jobId={} file={} bytes={}", userId, jobId, zipPath, len);

            StreamingResponseBody body = outputStream -> {
                try (InputStream in = new BufferedInputStream(new FileInputStream(zipPath.toFile()));
                     BufferedOutputStream out = new BufferedOutputStream(outputStream)) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        out.write(buf, 0, r);
                    }
                    out.flush();
                    log.info("[archive] download done userId={} jobId={}", userId, jobId);
                } catch (Exception ex) {
                    log.warn("[archive] download aborted userId={} jobId={} msg={}", userId, jobId, ex.getMessage());
                    throw ex;
                }
            };

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(len)
                .body(body);
        } catch (Exception e) {
            log.warn("[archive] download error jobId={} msg={}", jobId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @DeleteMapping("/archive")
    public ResponseEntity<?> deleteArchive(@RequestParam String token, @RequestParam String jobId) {
        try {
            String userId = requireUserId(token);
            archiveService.deleteJob(userId, jobId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}

package org.ejectfb.ejectcloud.service;

import org.ejectfb.ejectcloud.model.*;
import org.ejectfb.ejectcloud.util.TokenGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileStorageService {
    
    @Value("${ejectcloud.data-dir:./Data}")
    private String baseDir;
    
    @Value("${ejectcloud.share.expire-hours:24}")
    private int shareExpireHours;
    
    private final UserService userService;
    
    public FileStorageService(UserService userService) {
        this.userService = userService;
    }
    
    public UserService getUserService() {
        return userService;
    }
    
    private final Map<String, String> activeTokens = new ConcurrentHashMap<>();
    private final Map<String, Instant> tokenActivity = new ConcurrentHashMap<>();
    
    public String createToken(String telegramId) {
        String token = TokenGenerator.generateLong();
        activeTokens.put(token, telegramId);
        tokenActivity.put(token, Instant.now());
        return token;
    }
    
    public boolean isValidToken(String token) {
        return activeTokens.containsKey(token);
    }
    
    public void touchToken(String token) {
        if (activeTokens.containsKey(token)) {
            tokenActivity.put(token, Instant.now());
        }
    }
    
    public String getTelegramIdByToken(String token) {
        return activeTokens.get(token);
    }
    
    public void removeToken(String token) {
        activeTokens.remove(token);
        tokenActivity.remove(token);
    }
    
    public boolean hasActiveTokens() {
        return !activeTokens.isEmpty();
    }
    
    public Map<String, String> getActiveTokens() {
        return new java.util.HashMap<>(activeTokens);
    }
    
    public Map<String, Instant> getTokenActivity() {
        return new java.util.HashMap<>(tokenActivity);
    }
    
    public void cleanupInactiveTokens(Duration inactivityThreshold) {
        Instant cutoff = Instant.now().minus(inactivityThreshold);
        List<String> tokensToRemove = new ArrayList<>();
        
        tokenActivity.forEach((token, lastActivity) -> {
            if (lastActivity.isBefore(cutoff)) {
                tokensToRemove.add(token);
            }
        });
        
        tokensToRemove.forEach(this::removeToken);
    }
    
    private Path getUserDir(String telegramId) {
        Path dir = Paths.get(baseDir, telegramId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create user directory", e);
        }
        return dir;
    }
    
    private UserData loadUserData(String telegramId) {
        Path userFile = getUserDir(telegramId).resolve("user.xml");
        if (!Files.exists(userFile)) {
            return null;
        }
        
        try {
            JAXBContext context = JAXBContext.newInstance(UserData.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (UserData) unmarshaller.unmarshal(userFile.toFile());
        } catch (Exception e) {
            throw new RuntimeException("Cannot load user data", e);
        }
    }
    
    public void saveUserData(String telegramId, UserData userData) {
        Path userFile = getUserDir(telegramId).resolve("user.xml");
        try {
            JAXBContext context = JAXBContext.newInstance(UserData.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(userData, userFile.toFile());
        } catch (Exception e) {
            throw new RuntimeException("Cannot save user data", e);
        }
    }
    
    public boolean userExists(String telegramId) {
        Path userFile = getUserDir(telegramId).resolve("user.xml");
        return Files.exists(userFile);
    }
    
    public UserData getOrCreateUser(String userId, String username, long quotaBytes) {
        UserData userData = loadUserData(userId);
        if (userData == null) {
            userData = new UserData();
            userData.setId(userId);
            userData.setDisplayName(username);
            userData.setQuotaBytes(quotaBytes);
            userData.setCreatedAt(Instant.now().toString());
            saveUserData(userId, userData);
        }
        return userData;
    }
    
    public FileData uploadFile(String telegramId, MultipartFile file, String path) throws IOException {
        UserData userData = loadUserData(telegramId);
        if (userData == null) {
            userData = getOrCreateUser(telegramId, "user_" + telegramId, 1073741824L);
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalStateException("Некорректное имя файла");
        }
        
        // Валидация имени файла
        if (filename.matches(".*[<>:\"/\\|?*].*")) {
            throw new IllegalStateException("Имя файла содержит запрещенные символы: < > : \" / \\ | ? *");
        }
        
        long currentUsed = calculateTotalUsedBytes(telegramId);
        long fileSize = file.getSize();
        if (currentUsed + fileSize > userData.getQuotaBytes()) {
            double quotaGB = userData.getQuotaBytes() / 1024.0 / 1024.0 / 1024.0;
            double usedMB = currentUsed / 1024.0 / 1024.0;
            double fileMB = fileSize / 1024.0 / 1024.0;
            throw new IllegalStateException(
                String.format("Недостаточно места! Использовано: %.2f MB, файл: %.2f MB, квота: %.2f GB", 
                    usedMB, fileMB, quotaGB)
            );
        }
        
        Path dataDir = getUserDir(telegramId).resolve("data");
        if (path != null && !path.isEmpty()) {
            dataDir = dataDir.resolve(path);
        }
        
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать папку", e);
        }
        
        Path filePath = dataDir.resolve(filename);
        
        // Проверяем, существует ли файл с таким именем
        if (Files.exists(filePath)) {
            filename = generateUniqueFilename(dataDir, filename);
            filePath = dataDir.resolve(filename);
        }
        
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, filePath);
        }
        
        String relativePath = path != null && !path.isEmpty() ? path + "/" + filename : filename;
        
        FileData fileData = new FileData();
        fileData.setId(relativePath);
        fileData.setFilename(filename);
        fileData.setSizeBytes(fileSize);
        fileData.setUploadedAt(Instant.now().toString());
        
        return fileData;
    }
    
    public FileData uploadFile(String telegramId, MultipartFile file) throws IOException {
        return uploadFile(telegramId, file, null);
    }
    
    public List<FileData> listFiles(String telegramId) {
        List<FileData> files = new ArrayList<>();
        Path dataDir = getUserDir(telegramId).resolve("data");
        
        if (!Files.exists(dataDir)) {
            return files;
        }
        
        UserData userData = loadUserData(telegramId);
        Map<String, ShareData> shareMap = new HashMap<>();
        if (userData != null) {
            for (ShareData share : userData.getShares()) {
                if (Instant.parse(share.getExpiresAt()).isAfter(Instant.now())) {
                    shareMap.put(share.getFileId(), share);
                }
            }
        }
        
        try {
            Files.walk(dataDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String relativePath = dataDir.relativize(path).toString().replace("\\", "/");
                        FileData fileData = new FileData();
                        fileData.setId(relativePath);
                        fileData.setFilename(path.getFileName().toString());
                        fileData.setSizeBytes(Files.size(path));
                        fileData.setUploadedAt(Instant.ofEpochMilli(path.toFile().lastModified()).toString());
                        
                        ShareData share = shareMap.get(relativePath);
                        if (share != null) {
                            fileData.setShared(true);
                            fileData.setShareExpiresAt(share.getExpiresAt());
                        }
                        
                        files.add(fileData);
                    } catch (IOException e) {
                        // skip file
                    }
                });
        } catch (IOException e) {
            // return empty list
        }
        
        return files;
    }
    
    public Path getFilePath(String telegramId, String fileId) {
        return getUserDir(telegramId).resolve("data").resolve(fileId);
    }
    
    public void createDirectory(String telegramId, String path) throws IOException {
        Path dataDir = getUserDir(telegramId).resolve("data").resolve(path);
        Files.createDirectories(dataDir);
    }
    
    public void moveToTrash(String telegramId, String itemPath, boolean isFolder) throws IOException {
        Path sourcePath = getUserDir(telegramId).resolve("data").resolve(itemPath);
        if (!Files.exists(sourcePath)) {
            throw new IllegalStateException(isFolder ? "Папка не найдена" : "Файл не найден");
        }
        
        // Проверяем, есть ли уже такой файл в корзине
        Path trashDir = getUserDir(telegramId).resolve("trash");
        Path trashPath = trashDir.resolve(itemPath);
        
        if (Files.exists(trashPath)) {
            throw new IllegalStateException(isFolder ? "Папка с таким именем уже есть в корзине" : "Файл с таким именем уже есть в корзине");
        }
        
        // Удаляем ссылки на файлы
        removeSharesForPath(telegramId, itemPath, isFolder);
        
        // Перемещаем в корзину с сохранением структуры
        Files.createDirectories(trashDir);
        
        // Создаем родительские папки в корзине
        if (trashPath.getParent() != null) {
            Files.createDirectories(trashPath.getParent());
        }
        
        Files.move(sourcePath, trashPath);
    }
    
    private void removeSharesForPath(String telegramId, String itemPath, boolean isFolder) {
        UserData userData = loadUserData(telegramId);
        if (userData == null) return;
        
        boolean changed = userData.getShares().removeIf(share -> {
            if (isFolder) {
                return share.getFileId().startsWith(itemPath + "/") || share.getFileId().equals(itemPath);
            } else {
                return share.getFileId().equals(itemPath);
            }
        });
        
        if (changed) {
            saveUserData(telegramId, userData);
        }
    }
    
    public List<FileData> listTrash(String telegramId) {
        List<FileData> items = new ArrayList<>();
        Path trashDir = getUserDir(telegramId).resolve("trash");
        
        if (!Files.exists(trashDir)) {
            return items;
        }
        
        try {
            Files.walk(trashDir)
                .forEach(path -> {
                    try {
                        if (!path.equals(trashDir) && Files.isRegularFile(path)) {
                            String relativePath = trashDir.relativize(path).toString().replace("\\", "/");
                            FileData fileData = new FileData();
                            fileData.setId(relativePath);
                            fileData.setFilename(path.getFileName().toString());
                            fileData.setSizeBytes(Files.size(path));
                            fileData.setUploadedAt(Instant.ofEpochMilli(path.toFile().lastModified()).toString());
                            items.add(fileData);
                        }
                    } catch (IOException e) {
                        // skip item
                    }
                });
        } catch (IOException e) {
            // return empty list
        }
        
        return items;
    }
    
    public void clearTrash(String telegramId) throws IOException {
        Path trashDir = getUserDir(telegramId).resolve("trash");
        if (Files.exists(trashDir)) {
            Files.walk(trashDir)
                .sorted((a, b) -> b.compareTo(a)) // Удаляем сначала файлы, потом папки
                .forEach(path -> {
                    try {
                        if (!path.equals(trashDir)) {
                            Files.delete(path);
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }
    
    public void deleteFromTrash(String telegramId, String itemId) throws IOException {
        Path itemPath = getUserDir(telegramId).resolve("trash").resolve(itemId);
        if (!Files.exists(itemPath)) {
            throw new IllegalStateException("Элемент не найден в корзине");
        }
        
        if (Files.isDirectory(itemPath)) {
            Files.walk(itemPath)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        } else {
            Files.delete(itemPath);
        }
    }
    
    public void restoreFromTrash(String telegramId, String itemId) throws IOException {
        Path trashPath = getUserDir(telegramId).resolve("trash").resolve(itemId);
        if (!Files.exists(trashPath)) {
            throw new IllegalStateException("Элемент не найден в корзине");
        }
        
        Path dataPath = getUserDir(telegramId).resolve("data").resolve(itemId);
        
        // Создаем родительские папки если нужно
        if (dataPath.getParent() != null && !dataPath.getParent().equals(getUserDir(telegramId).resolve("data"))) {
            Files.createDirectories(dataPath.getParent());
        }
        
        // Проверяем, что целевой файл не существует
        if (Files.exists(dataPath)) {
            throw new IllegalStateException("Файл с таким именем уже существует");
        }
        
        Files.move(trashPath, dataPath);
        
        // Удаляем пустые папки в корзине
        cleanupEmptyTrashFolders(telegramId, itemId);
    }
    
    private void cleanupEmptyTrashFolders(String telegramId, String restoredItemId) {
        try {
            Path trashDir = getUserDir(telegramId).resolve("trash");
            if (!restoredItemId.contains("/")) {
                return; // Нет папок для очистки
            }
            
            String currentPath = restoredItemId.substring(0, restoredItemId.lastIndexOf("/"));
            
            while (currentPath != null && !currentPath.isEmpty()) {
                Path currentDir = trashDir.resolve(currentPath);
                if (Files.exists(currentDir) && Files.isDirectory(currentDir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
                        if (!stream.iterator().hasNext()) {
                            Files.delete(currentDir);
                            // Переходим к родительской папке
                            if (currentPath.contains("/")) {
                                currentPath = currentPath.substring(0, currentPath.lastIndexOf("/"));
                            } else {
                                break;
                            }
                        } else {
                            break; // Папка не пуста
                        }
                    }
                } else {
                    break; // Папка не существует
                }
            }
        } catch (IOException e) {
            // Игнорируем ошибки очистки
        }
    }
    
    public long calculateTotalUsedBytes(String telegramId) {
        long dataBytes = calculateUsedBytes(telegramId);
        long trashBytes = 0;
        
        Path trashDir = getUserDir(telegramId).resolve("trash");
        if (Files.exists(trashDir)) {
            try {
                trashBytes = Files.walk(trashDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
            } catch (IOException e) {
                // ignore
            }
        }
        
        return dataBytes + trashBytes;
    }
    
    public List<String> listFolders(String telegramId) {
        List<String> folders = new ArrayList<>();
        Path dataDir = getUserDir(telegramId).resolve("data");
        
        if (!Files.exists(dataDir)) {
            return folders;
        }
        
        try {
            Files.walk(dataDir)
                .filter(Files::isDirectory)
                .filter(path -> !path.equals(dataDir))
                .forEach(path -> {
                    String relativePath = dataDir.relativize(path).toString().replace("\\", "/");
                    folders.add(relativePath);
                });
        } catch (IOException e) {
            // ignore
        }
        
        folders.sort(String::compareTo);
        return folders;
    }
    
    public List<String> listTrashFolders(String telegramId) {
        List<String> folders = new ArrayList<>();
        Path trashDir = getUserDir(telegramId).resolve("trash");
        
        if (!Files.exists(trashDir)) {
            return folders;
        }
        
        try {
            Files.walk(trashDir)
                .filter(Files::isDirectory)
                .filter(path -> !path.equals(trashDir))
                .forEach(path -> {
                    String relativePath = trashDir.relativize(path).toString().replace("\\", "/");
                    folders.add(relativePath);
                });
        } catch (IOException e) {
            // ignore
        }
        
        folders.sort(String::compareTo);
        return folders;
    }
    
    public long calculateUsedBytes(String telegramId) {
        Path dataDir = getUserDir(telegramId).resolve("data");
        if (!Files.exists(dataDir)) {
            return 0;
        }
        
        try {
            return Files.walk(dataDir)
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
        } catch (IOException e) {
            return 0;
        }
    }
    
    public String createShare(String telegramId, String fileId) {
        UserData userData = loadUserData(telegramId);
        if (userData == null) {
            throw new IllegalStateException("User not found");
        }
        
        Path filePath = getFilePath(telegramId, fileId);
        if (!Files.exists(filePath)) {
            throw new IllegalStateException("File not found");
        }
        
        // Проверяем, есть ли активная ссылка
        for (ShareData share : userData.getShares()) {
            if (share.getFileId().equals(fileId) && 
                Instant.parse(share.getExpiresAt()).isAfter(Instant.now())) {
                return share.getShareId();
            }
        }
        
        // Создаем новую ссылку
        String shareId = TokenGenerator.generateLong();
        ShareData shareData = new ShareData();
        shareData.setShareId(shareId);
        shareData.setFileId(fileId);
        shareData.setCreatedAt(Instant.now().toString());
        shareData.setExpiresAt(Instant.now().plus(Duration.ofHours(shareExpireHours)).toString());
        
        userData.getShares().add(shareData);
        saveUserData(telegramId, userData);
        
        return shareId;
    }
    
    public void deleteShare(String telegramId, String fileId) {
        UserData userData = loadUserData(telegramId);
        if (userData == null) {
            throw new IllegalStateException("User not found");
        }
        
        boolean removed = userData.getShares().removeIf(share -> share.getFileId().equals(fileId));
        if (removed) {
            saveUserData(telegramId, userData);
        }
    }
    
    public void cleanupExpiredShares() {
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(Paths.get(baseDir))) {
            for (Path userDir : dirs) {
                if (Files.isDirectory(userDir)) {
                    String telegramId = userDir.getFileName().toString();
                    UserData userData = loadUserData(telegramId);
                    if (userData != null) {
                        boolean changed = userData.getShares().removeIf(
                            share -> Instant.parse(share.getExpiresAt()).isBefore(Instant.now())
                        );
                        if (changed) {
                            saveUserData(telegramId, userData);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }
    
    private String generateUniqueFilename(Path directory, String originalFilename) {
        String name = originalFilename;
        String extension = "";
        
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot > 0) {
            name = originalFilename.substring(0, lastDot);
            extension = originalFilename.substring(lastDot);
        }
        
        int counter = 1;
        String newFilename = originalFilename;
        
        while (Files.exists(directory.resolve(newFilename))) {
            newFilename = name + " (" + counter + ")" + extension;
            counter++;
        }
        
        return newFilename;
    }
    
    public String moveFile(String telegramId, String fileId, String targetFolder) throws IOException {
        Path sourcePath = getUserDir(telegramId).resolve("data").resolve(fileId);
        if (!Files.exists(sourcePath)) {
            throw new IllegalStateException("Файл не найден");
        }
        
        String originalFileName = sourcePath.getFileName().toString();
        String fileName = originalFileName;
        Path targetDir = getUserDir(telegramId).resolve("data");
        
        if (targetFolder != null && !targetFolder.isEmpty() && !targetFolder.equals("/")) {
            targetDir = targetDir.resolve(targetFolder);
            if (!Files.exists(targetDir)) {
                throw new IllegalStateException("Целевая папка не найдена");
            }
        }
        
        Path targetPath = targetDir.resolve(fileName);
        boolean wasRenamed = false;
        
        // Проверяем, что файл с таким именем не существует
        if (Files.exists(targetPath)) {
            fileName = generateUniqueFilename(targetDir, fileName);
            targetPath = targetDir.resolve(fileName);
            wasRenamed = true;
        }
        
        Files.move(sourcePath, targetPath);
        
        // Обновляем ссылки
        UserData userData = loadUserData(telegramId);
        if (userData != null) {
            String newFileId = targetFolder != null && !targetFolder.isEmpty() && !targetFolder.equals("/") 
                ? targetFolder + "/" + fileName : fileName;
            
            for (ShareData share : userData.getShares()) {
                if (share.getFileId().equals(fileId)) {
                    share.setFileId(newFileId);
                }
            }
            saveUserData(telegramId, userData);
        }
        
        return wasRenamed ? fileName : null;
    }

    public FileData getFileByShare(String shareId) {
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(Paths.get(baseDir))) {
            for (Path userDir : dirs) {
                if (Files.isDirectory(userDir)) {
                    String telegramId = userDir.getFileName().toString();
                    UserData userData = loadUserData(telegramId);
                    if (userData != null && userData.getShares() != null) {
                        for (ShareData share : userData.getShares()) {
                            if (share.getShareId().equals(shareId)) {
                                // Проверяем, не истекла ли ссылка
                                if (Instant.parse(share.getExpiresAt()).isBefore(Instant.now())) {
                                    return null; // Ссылка истекла
                                }
                                
                                Path filePath = getFilePath(telegramId, share.getFileId());
                                if (Files.exists(filePath)) {
                                    try {
                                        FileData fileData = new FileData();
                                        fileData.setId(share.getFileId());
                                        fileData.setFilename(filePath.getFileName().toString());
                                        fileData.setSizeBytes(Files.size(filePath));
                                        fileData.setUploadedAt(Instant.ofEpochMilli(filePath.toFile().lastModified()).toString());
                                        fileData.setTelegramId(telegramId);
                                        return fileData;
                                    } catch (IOException e) {
                                        return null;
                                    }
                                }
                                return null; // Файл не найден
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }
    
    private void validateFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя не может быть пустым");
        }
        
        String trimmed = name.trim();
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("Имя слишком длинное (максимум 255 символов)");
        }
        
        if (trimmed.matches(".*[<>:\"/\\|?*].*")) {
            throw new IllegalArgumentException("Имя содержит запрещенные символы: < > : \" / \\ | ? *");
        }
        
        if (trimmed.equals(".") || trimmed.equals("..")) {
            throw new IllegalArgumentException("Недопустимое имя");
        }
        
        // Проверка на зарезервированные имена Windows
        String upperName = trimmed.toUpperCase();
        String[] reserved = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};
        for (String res : reserved) {
            if (upperName.equals(res) || upperName.startsWith(res + ".")) {
                throw new IllegalArgumentException("Зарезервированное имя: " + res);
            }
        }
    }
    
    public void renameFile(String telegramId, String fileId, String newName) throws IOException {
        validateFileName(newName);
        
        Path filePath = getFilePath(telegramId, fileId);
        if (!Files.exists(filePath)) {
            throw new IllegalStateException("Файл не найден");
        }
        
        // Определяем новый путь
        Path parentDir = filePath.getParent();
        Path newFilePath = parentDir.resolve(newName);
        
        if (Files.exists(newFilePath)) {
            throw new IllegalStateException("Файл с таким именем уже существует");
        }
        
        // Переименовываем файл
        Files.move(filePath, newFilePath);
        
        // Обновляем ссылки
        UserData userData = loadUserData(telegramId);
        if (userData != null) {
            String newFileId = fileId.contains("/") ? 
                fileId.substring(0, fileId.lastIndexOf("/") + 1) + newName : newName;
            
            for (ShareData share : userData.getShares()) {
                if (share.getFileId().equals(fileId)) {
                    share.setFileId(newFileId);
                }
            }
            saveUserData(telegramId, userData);
        }
    }
    
    public void renameFolder(String telegramId, String folderPath, String newName) throws IOException {
        validateFileName(newName);
        
        Path dataDir = getUserDir(telegramId).resolve("data");
        Path folderFullPath = dataDir.resolve(folderPath);
        
        if (!Files.exists(folderFullPath) || !Files.isDirectory(folderFullPath)) {
            throw new IllegalStateException("Папка не найдена");
        }
        
        // Определяем новый путь
        Path parentDir = folderFullPath.getParent();
        Path newFolderPath = parentDir.resolve(newName);
        
        if (Files.exists(newFolderPath)) {
            throw new IllegalStateException("Папка с таким именем уже существует");
        }
        
        // Переименовываем папку
        Files.move(folderFullPath, newFolderPath);
        
        // Обновляем ссылки на файлы в этой папке
        UserData userData = loadUserData(telegramId);
        if (userData != null) {
            String newFolderRelativePath = folderPath.contains("/") ? 
                folderPath.substring(0, folderPath.lastIndexOf("/") + 1) + newName : newName;
            
            for (ShareData share : userData.getShares()) {
                if (share.getFileId().startsWith(folderPath + "/")) {
                    String relativePart = share.getFileId().substring(folderPath.length() + 1);
                    share.setFileId(newFolderRelativePath + "/" + relativePart);
                }
            }
            saveUserData(telegramId, userData);
        }
    }
}
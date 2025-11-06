package org.ejectfb.ejectcloud.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.ejectfb.ejectcloud.model.UserData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class TelegramBotHandler {
    
    @Value("${telegram.bot.token:}")
    private String botToken;
    
    @Value("${telegram.admin.chatid:}")
    private String adminChatId;
    
    @Value("${ejectcloud.base-url:http://localhost:8080}")
    private String baseUrl;
    
    @Value("${ejectcloud.default.quota:1073741824}")
    private long defaultQuota;
    
    private final FileStorageService storageService;
    private final Map<String, String> pendingRegistrations = new ConcurrentHashMap<>();
    private TelegramBot bot;
    
    public TelegramBotHandler(FileStorageService storageService) {
        this.storageService = storageService;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void startBot() {
        if (botToken == null || botToken.isEmpty()) return;
        
        bot = new TelegramBot(botToken);
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                if (update.message() != null && update.message().text() != null) {
                    handleMessage(update);
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }
    
    private void handleMessage(Update update) {
        String chatId = String.valueOf(update.message().chat().id());
        String text = update.message().text();
        String username = update.message().from().username();
        
        if (chatId.equals(adminChatId)) {
            if (text.startsWith("/")) {
                handleAdminCommand(chatId, text);
            } else {
                sendMessage(chatId, "Команды админа:\n/approve <chat_id> - одобрить пользователя\n/stats - статистика\n/link - ваша ссылка на UI");
            }
            return;
        }
        
        if (text.startsWith("/start")) {
            handleUserRequest(chatId, username);
        } else if (text.equals("/link")) {
            handleLinkRequest(chatId);
        }
    }
    
    private void handleUserRequest(String chatId, String username) {
        if (storageService.userExists(chatId)) {
            sendMessage(chatId, "У вас уже есть доступ! Для получения ссылки отправьте /link");
        } else {
            pendingRegistrations.put(chatId, username != null ? username : "user_" + chatId);
            sendMessage(chatId, "Заявка на доступ отправлена администратору. Ожидайте подтверждения.");
            
            if (!adminChatId.isEmpty()) {
                sendMessage(adminChatId, 
                    "Новая заявка на доступ:\n" +
                    "Username: " + (username != null ? username : "не указан") + "\n" +
                    "Chat ID: " + chatId + "\n\n" +
                    "Для одобрения: /approve " + chatId);
            }
        }
    }
    
    private void handleLinkRequest(String chatId) {
        // Админ получает ссылку сразу
        if (chatId.equals(adminChatId)) {
            String token = storageService.createToken(chatId);
            String loginLink = baseUrl + "/?token=" + token;
            sendMessage(chatId, "Админ-панель: " + loginLink);
        } else if (storageService.userExists(chatId)) {
            String token = storageService.createToken(chatId);
            String loginLink = baseUrl + "/?token=" + token;
            sendMessage(chatId, "Ваша ссылка для входа: " + loginLink);
        } else {
            sendMessage(chatId, "У вас нет доступа. Отправьте /start для запроса доступа.");
        }
    }
    
    private void handleAdminCommand(String chatId, String text) {
        if (text.startsWith("/approve ")) {
            String targetChatId = text.substring(9).trim();
            String username = pendingRegistrations.get(targetChatId);
            if (username != null) {
                try {
                    UserData userData = storageService.getOrCreateUser(targetChatId, username, defaultQuota);
                    String token = storageService.createToken(targetChatId);
                    String loginLink = baseUrl + "/?token=" + token;
                    
                    sendMessage(chatId, "Пользователь " + targetChatId + " одобрен");
                    sendMessage(targetChatId, "Доступ одобрен! Ваша ссылка: " + loginLink + "\n\nДля получения новых ссылок используйте /link");
                    pendingRegistrations.remove(targetChatId);
                } catch (Exception e) {
                    sendMessage(chatId, "Ошибка: " + e.getMessage());
                }
            } else {
                sendMessage(chatId, "Заявка не найдена");
            }
        } else if (text.equals("/link")) {
            String token = storageService.createToken(chatId);
            String loginLink = baseUrl + "/?token=" + token;
            sendMessage(chatId, "Админ-панель: " + loginLink);
        } else if (text.equals("/stats")) {
            try {
                int userCount = 0;
                long totalUsed = 0;
                java.nio.file.DirectoryStream<java.nio.file.Path> dirs = java.nio.file.Files.newDirectoryStream(java.nio.file.Paths.get("./Data"));
                for (java.nio.file.Path userDir : dirs) {
                    if (java.nio.file.Files.isDirectory(userDir)) {
                        userCount++;
                        String telegramId = userDir.getFileName().toString();
                        totalUsed += storageService.calculateUsedBytes(telegramId);
                    }
                }
                
                String stats = String.format("Статистика:\nПользователей: %d\nИспользовано: %.2f MB\nАктивных сессий: %d", 
                    userCount, totalUsed / 1024.0 / 1024.0, storageService.hasActiveTokens() ? 1 : 0);
                sendMessage(chatId, stats);
            } catch (Exception e) {
                sendMessage(chatId, "Ошибка получения статистики");
            }
        }
    }
    
    public void sendMessage(String chatId, String text) {
        if (bot != null) {
            bot.execute(new SendMessage(chatId, text));
        }
    }
}
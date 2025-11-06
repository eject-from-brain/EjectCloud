package org.ejectfb.ejectcloud.config;

import org.ejectfb.ejectcloud.service.FileStorageService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class StealthFilter implements Filter {
    
    private final FileStorageService storageService;
    
    public StealthFilter(FileStorageService storageService) {
        this.storageService = storageService;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        
        // Разрешаем админские пути, статические ресурсы и публичные ссылки всегда
        String path = req.getRequestURI();
        if (path.startsWith("/admin") || path.startsWith("/h2-console") || path.startsWith("/share/") ||
            path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".html") ||
            path.endsWith(".ico") || path.equals("/")) {
            chain.doFilter(request, response);
            return;
        }
        
        // Проверяем наличие валидного токена в запросе
        String token = req.getParameter("token");
        
        if (token != null && storageService.isValidToken(token)) {
            // Есть валидный токен - обновляем активность и пропускаем запрос
            storageService.touchToken(token);
            chain.doFilter(request, response);
            return;
        }
        
        // Для всех остальных API запросов без валидного токена - 404
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
}
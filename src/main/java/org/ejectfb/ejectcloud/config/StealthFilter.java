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
        
        String path = req.getRequestURI();
        
        // Разрешаем статические ресурсы, логин и публичные ссылки
        if (path.startsWith("/share/") || path.endsWith(".js") || path.endsWith(".css") || 
            path.endsWith(".html") || path.endsWith(".ico") || path.equals("/") ||
            path.startsWith("/api/auth/") || path.startsWith("/login")) {
            chain.doFilter(request, response);
            return;
        }
        
        // Для API запросов требуем токен
        if (path.startsWith("/api/")) {
            String token = req.getParameter("token");
            if (token == null) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        
        chain.doFilter(request, response);
    }
}
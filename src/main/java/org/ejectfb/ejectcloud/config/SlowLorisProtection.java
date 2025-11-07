package org.ejectfb.ejectcloud.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(1)
public class SlowLorisProtection extends OncePerRequestFilter {
    
    private final ConcurrentHashMap<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();
    private final int MAX_CONNECTIONS_PER_IP = 10;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String clientIp = getClientIP(request);
        AtomicInteger count = connectionCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        
        if (count.incrementAndGet() > MAX_CONNECTIONS_PER_IP) {
            count.decrementAndGet();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write("Too many concurrent connections");
            return;
        }
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            count.decrementAndGet();
            // Очищаем неактивные IP
            if (count.get() <= 0) {
                connectionCounts.remove(clientIp);
            }
        }
    }
    
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
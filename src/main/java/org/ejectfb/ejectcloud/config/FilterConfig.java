package org.ejectfb.ejectcloud.config;

import org.ejectfb.ejectcloud.service.FileStorageService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {
    
    @Bean
    public StealthFilter stealthFilter(FileStorageService storageService) {
        return new StealthFilter(storageService);
    }
    
    @Bean
    public FilterRegistrationBean<StealthFilter> stealthFilterRegistration(StealthFilter filter) {
        FilterRegistrationBean<StealthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }
}
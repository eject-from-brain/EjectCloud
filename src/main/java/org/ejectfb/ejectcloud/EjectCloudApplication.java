package org.ejectfb.ejectcloud;

import org.ejectfb.ejectcloud.service.UserService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;


@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "org.ejectfb.ejectcloud")
public class EjectCloudApplication {
    public static void main(String[] args) {
        System.out.println("Starting EjectCloud application...");
        try {
            SpringApplication.run(EjectCloudApplication.class, args);
            System.out.println("EjectCloud application started successfully!");
        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Component
    public static class StartupInitializer {
        private final UserService userService;
        
        public StartupInitializer(UserService userService) {
            this.userService = userService;
        }
        
        @EventListener(ApplicationReadyEvent.class)
        public void onApplicationReady() {
            userService.initializeDefaultAdmin();
        }
    }
    

}

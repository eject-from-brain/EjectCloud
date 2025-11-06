package org.ejectfb.ejectcloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EjectCloudApplication {
    public static void main(String[] args) {
        SpringApplication.run(EjectCloudApplication.class, args);
    }
}

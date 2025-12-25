package com.demo.accessiblenav;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AccessibleNavApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessibleNavApplication.class, args);
    }
}

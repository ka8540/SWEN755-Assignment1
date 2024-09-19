package com.assignment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Assignment1Application {

    public static void main(String[] args) {
        SpringApplication.run(Assignment1Application.class, args);
    }
}
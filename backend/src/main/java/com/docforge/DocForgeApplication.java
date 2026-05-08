package com.docforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DocForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocForgeApplication.class, args);
    }
}

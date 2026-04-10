package fr.enseeiht.jeux; // On a changé le package ici

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BackendCartesApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendCartesApplication.class, args);
    }
}
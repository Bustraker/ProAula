package com.proaula.aula;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.proaula.aula.Entity")
@EnableJpaRepositories(basePackages = "com.proaula.aula.Repository")
public class AulaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AulaApplication.class, args);
    }
}
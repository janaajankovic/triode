package dev.triode;

import org.springframework.boot.SpringApplication;

public class TestTriodeApplication {

    public static void main(String[] args) {
        SpringApplication.from(TriodeApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}

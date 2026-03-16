package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@RestController
class HelloController {

    @Autowired
    private Environment environment;

    @GetMapping("/")
    public String hello() {
        return "Hello CI/CD World! - Entrega Final";
    }

    @GetMapping("/health")
    public String health() {
        return "Health check passed!";
    }

    @GetMapping("/instance")
    public String instance() {
        String port = environment.getProperty("local.server.port");
        return "Instancia corriendo en el puerto: " + port;
    }

    @GetMapping("/version")
    public String version() {
        return "Versión Servicio CI/CD: 04.16.03.2026";
    }
}

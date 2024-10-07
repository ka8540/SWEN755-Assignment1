package com.assignment.controller;

import com.assignment.model.Health;
import com.assignment.repository.HealthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/health")
public class HealthController {

    @Autowired
    private HealthRepository healthRepository;

    @Autowired
    private RestTemplate restTemplate; // Reuse RestTemplate from a Bean

    @GetMapping
    public ResponseEntity<Health> getHealthStatus() {
        Health health = healthRepository.findFirstByOrderByIdDesc();

        try {
            // Check if /response is alive
            ResponseEntity<String> responseAlive = restTemplate.getForEntity("http://localhost:8080/response",
                    String.class);
            if (responseAlive.getStatusCode().is2xxSuccessful()) {
                System.out.println("Response service is alive: " + responseAlive.getBody());
            }
        } catch (Exception e) {
            System.out.println("Response service is down.");
        }

        try {
            // Check if /client is alive
            ResponseEntity<String> clientAlive = restTemplate.getForEntity("http://localhost:8080/client",
                    String.class);
            if (clientAlive.getStatusCode().is2xxSuccessful()) {
                System.out.println("Client service is alive: " + clientAlive.getBody());
            }
        } catch (Exception e) {
            System.out.println("Client service is down.");
        }

        return ResponseEntity.ok(health);
    }
}

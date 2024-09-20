package com.assignment.controller;

import com.assignment.model.Health;
import com.assignment.repository.HealthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @Autowired
    private HealthRepository healthRepository;

    @GetMapping
    public ResponseEntity<Health> getHealthStatus() {
        Health health = healthRepository.findFirstByOrderByIdDesc();
        return ResponseEntity.ok(health);
    }
}

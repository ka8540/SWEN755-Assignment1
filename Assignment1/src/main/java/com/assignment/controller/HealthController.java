package com.assignment.controller;

import com.assignment.model.Health;
import com.assignment.repository.HealthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/health")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private HealthRepository healthRepository;

    @Autowired
    private RestTemplate restTemplate; // Reuse RestTemplate from a Bean

    private boolean instance1Alive = true;
    private boolean instance2Alive = true;

    @GetMapping
    public ResponseEntity<Health> getHealthStatus() {
        Health health = healthRepository.findFirstByOrderByIdDesc();

        if (health != null) {
            health.getResponses().size(); // This will force the responses to be loaded
        }

        logger.info("Instance 1 is " + (instance1Alive ? "alive" : "down"));
        logger.info("Instance 2 is " + (instance2Alive ? "alive" : "down"));

        return ResponseEntity.ok(health);
    }

    // Scheduled method to check health of instances every 5 seconds
    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void monitorInstances() {
        checkInstanceHealth("http://localhost:8080/health", "Instance 1");
        checkInstanceHealth("http://localhost:8081/health", "Instance 2");
    }

    private void checkInstanceHealth(String url, String instanceName) {
        try {
            ResponseEntity<Health> response = restTemplate.getForEntity(url, Health.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                if ("Instance 1".equals(instanceName)) {
                    instance1Alive = true;
                } else if ("Instance 2".equals(instanceName)) {
                    instance2Alive = true;
                }
            }
        } catch (Exception e) {
            logger.error("{} is down: {}", instanceName, e.getMessage());
            if ("Instance 1".equals(instanceName)) {
                instance1Alive = false;
                // Handle fault recovery for Instance 1 here
                handleFaultRecovery("Instance 1", 8081);
            } else if ("Instance 2".equals(instanceName)) {
                instance2Alive = false;
                // Handle fault recovery for Instance 2 here
                handleFaultRecovery("Instance 2", 8082);
            }
        }
    }

    private void handleFaultRecovery(String instanceName, int port) {
        logger.info("Initiating fault recovery for {}", instanceName);

        // Update internal state to mark the instance as down
        if ("Instance 1".equals(instanceName)) {
            instance1Alive = false;
        } else if ("Instance 2".equals(instanceName)) {
            instance2Alive = false;
        }

        // Simulate notification to administrators or alerting system
        logger.info("Sending alert: {} is down", instanceName);
    }

    // Endpoint to handle instance-down notifications from instances
    @PostMapping("/instance-down")
    public ResponseEntity<String> instanceDown(@RequestParam int port) {
        if (port == 8080) {
            instance1Alive = false;
            logger.info("Received notification that Instance 1 is down.");
            handleFaultRecovery("Instance 1", port);
        } else if (port == 8081) {
            instance2Alive = false;
            logger.info("Received notification that Instance 2 is down.");
            handleFaultRecovery("Instance 2", port);
        } else {
            logger.warn("Received notification for unknown instance on port {}", port);
        }
        return ResponseEntity.ok("Instance status updated.");
    }
}

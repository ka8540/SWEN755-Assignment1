package com.assignment.controller;

import com.assignment.model.Client;
import com.assignment.model.Health;
import com.assignment.model.Response;
import com.assignment.repository.ClientRepository;
import com.assignment.repository.HealthRepository;
import com.assignment.service.ResponseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Random;

@RestController
@RequestMapping("/client")
public class ClientController {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ResponseService responseService;

    @Autowired
    private HealthRepository healthRepository;

    // POST endpoint to send random data to /response and create a client record
    @PostMapping
    public ResponseEntity<?> sendRandomDataToResponse() {
        try {
            // Generate random data
            String randomData = generateRandomData();
            
            // Create a new client record
            Client client = new Client();
            client.setData(randomData);
            clientRepository.save(client);
            
            // to send the random data to /response
            Response response = responseService.saveRandomResponse(randomData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing request: " + e.getMessage());
        }
    }

    // Respond to the health server's "Are you alive?" check
    @GetMapping("/health")
    public ResponseEntity<String> checkHealth() {
        Health health = healthRepository.findFirstByOrderByIdDesc();
        if (health != null && (health.getFlag() == 1 || health.getFlag() == null)) {
            return ResponseEntity.ok("200 OK: Client is alive");
        } else {
            return ResponseEntity.status(404).body("404 Not Found: Client is not alive");
        }
    }

    // Generate random data for the client
    private String generateRandomData() {
        int length = 10; // Length of the random string
        StringBuilder sb = new StringBuilder(length);
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            char randomChar = (char) ('a' + random.nextInt(26));
            sb.append(randomChar);
        }
        return sb.toString();
    }
}
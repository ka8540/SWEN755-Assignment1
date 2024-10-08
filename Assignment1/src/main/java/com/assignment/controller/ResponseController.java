package com.assignment.controller;

import com.assignment.model.Response;
import com.assignment.service.ResponseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus; // Import HttpStatus
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/response")
public class ResponseController {

    @Autowired
    private ResponseService responseService;

    // POST endpoint for creating random responses
    @PostMapping
    public ResponseEntity<Response> createResponse() {
        // Check if the service is alive
        if (!responseService.getIsAlive()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
        }

        // Generate random data using the service's method
        String randomData = responseService.generateRandomString();

        // Call saveRandomResponse with the random data
        Response response = responseService.saveRandomResponse(randomData);

        return ResponseEntity.ok(response);
    }

    // GET endpoint to handle health checks or status queries
    @GetMapping
    public ResponseEntity<String> checkResponseStatus() {
        // Return the current status of the service
        boolean status = responseService.getIsAlive();
        return ResponseEntity.ok("Response service is " + (status ? "alive" : "down"));
    }

    // Endpoint to set isAlive status
    @PostMapping("/setAlive")
    public ResponseEntity<String> setAlive(@RequestParam boolean isAlive) {
        responseService.setIsAlive(isAlive);
        return ResponseEntity.ok("Response service is now " + (isAlive ? "alive" : "down"));
    }

    // Optional: Endpoint to get isAlive status directly
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        boolean status = responseService.getIsAlive();
        return ResponseEntity.ok("Response service is " + (status ? "alive" : "down"));
    }
}

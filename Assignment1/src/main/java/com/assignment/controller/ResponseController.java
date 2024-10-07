package com.assignment.controller;

import com.assignment.model.Response;
import com.assignment.service.ResponseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/response")
public class ResponseController {

    @Autowired
    private ResponseService responseService;

    // POST endpoint for creating random responses
    @PostMapping
    public ResponseEntity<Response> createResponse() {
        // Generate random data using the service's method
        String randomData = responseService.generateRandomString();

        // Call saveRandomResponse with the random data
        Response response = responseService.saveRandomResponse(randomData);

        return ResponseEntity.ok(response);
    }

    // GET endpoint to handle health checks or status queries
    @GetMapping
    public ResponseEntity<String> checkResponseStatus() {
        // You can add any custom logic here if needed, for now, it's just a simple
        // check
        return ResponseEntity.ok("Response service is alive");
    }

    // Endpoint to receive synchronization data from replica
    @PostMapping("/replica-sync")
    public ResponseEntity<String> syncFromReplica(@RequestBody String data) {
        responseService.processReplicaData(data);
        return ResponseEntity.ok("Data synchronized from replica.");
    }

    // Endpoint to provide random number for fault recovery
    @GetMapping("/random-number")
    public ResponseEntity<Integer> getRandomNumber() {
        int randomNumber = responseService.getRandomNumber();
        return ResponseEntity.ok(randomNumber);
    }
}

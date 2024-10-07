package com.assignment.service;

import com.assignment.model.Response;
import com.assignment.model.Health;
import com.assignment.repository.ResponseRepository;
import com.assignment.repository.HealthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class ResponseService {

    private static final Logger logger = LoggerFactory.getLogger(ResponseService.class);

    @Autowired
    private ResponseRepository responseRepository;

    @Autowired
    private HealthRepository healthRepository;

    private final int MAX_REQUESTS_PER_MINUTE = 20;
    private final int MAX_ALLOWED_DIFF = 60;

    private int requestsInCurrentWindow = 0;
    private LocalDateTime windowStartTime = LocalDateTime.now();
    private boolean responseAlive = true; // Track if /response is up

    private Random random = new Random();
    private RestTemplate restTemplate = new RestTemplate();

    @Scheduled(fixedRate = 60000)
    public void generateAndSendRandomRequests() {
        if (!responseAlive) {
            logger.info("System is down. No further requests will be sent to /response.");
            return; // Stop sending requests if /response is down
        }

        int requestsToSendInMinute = random.nextInt(101); // Random number of requests to send
        // Adjust the interval based on the number of requests
        int baseIntervalInMs = 1000; // Minimum 1 second between requests
        int intervalInMs = 60000 / Math.max(requestsToSendInMinute, 1); // Calculate basic interval
        intervalInMs = Math.max(intervalInMs, baseIntervalInMs); // Ensure at least 1 second between each request

        logger.info("Requests to send in this minute: " + requestsToSendInMinute);

        for (int i = 0; i < requestsToSendInMinute; i++) {
            boolean requestSuccessful = sendRandomDataToResponse();
            if (requestSuccessful) {
                logger.info("Request to /response successful. Sending data to /health.");
                sendDataToHealth();
            } else {
                logger.info("Request to /response failed. Halting further requests and checking /response status.");
                checkResponseHealth();
                break; // Exit the loop and stop further processing after a failure
            }

            if (i % 40 == 39) { // After every 40 requests, take a 3-second break
                try {
                    Thread.sleep(3000); // Wait for 3 seconds
                } catch (InterruptedException e) {
                    logger.error("Interrupted during scheduled pause: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }

            try {
                Thread.sleep(intervalInMs); // Wait before sending the next request
            } catch (InterruptedException e) {
                logger.error("Interrupted during sleep: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Number of requests processed in this minute: " + requestsToSendInMinute);
    }

    // Sends the random data to /response and returns true if successful
    private boolean sendRandomDataToResponse() {
        String randomData = generateRandomString();
        logger.info("Generated Random Data: " + randomData);

        try {
            // Send POST request to /response
            ResponseEntity<Response> response = restTemplate.postForEntity("http://localhost:8080/response", randomData,
                    Response.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Data successfully sent to /response: " + randomData);
                saveRandomResponse(randomData); // Save the data locally
                return true;
            }
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                logger.info("/response returned 404. No further requests.");
                responseAlive = false; // Mark /response as down
            } else {
                logger.error("Error while sending data to /response: " + e.getMessage());
            }
        }
        return false;
    }

    // Sends a GET request to /health to inform about the successful data transfer
    private void sendDataToHealth() {
        try {
            ResponseEntity<String> healthResponse = restTemplate.getForEntity("http://localhost:8080/health",
                    String.class);
            if (healthResponse.getStatusCode().is2xxSuccessful()) {
                logger.info("Data successfully sent to /health. Client is alive.");
            } else {
                logger.warn("Failed to send data to /health. Status code: " + healthResponse.getStatusCode().value());
            }
        } catch (HttpStatusCodeException e) {
            logger.error("Error sending data to /health: " + e.getMessage());
        }
    }

    // Check if /response is alive
    private void checkResponseHealth() {
        try {
            ResponseEntity<String> responseHealthCheck = restTemplate.getForEntity("http://localhost:8080/response",
                    String.class);
            if (responseHealthCheck.getStatusCode().is2xxSuccessful()) {
                logger.info("/response is still alive.");
                responseAlive = true; // Mark response as alive
            } else {
                logger.error("/response is down. Status code: " + responseHealthCheck.getStatusCode().value());
                responseAlive = false;
                logger.info("System is down.");
            }
        } catch (HttpStatusCodeException e) {
            logger.error("/response is down. Error while checking /response status: " + e.getMessage());
            responseAlive = false;
            logger.info("System is down.");
        }
    }

    public Response saveRandomResponse(String randomData) {
        Health health = healthRepository.findFirstByOrderByIdDesc();

        if (health == null) {
            health = new Health();
            health.setNumRequests(0);
            health.setFlag(1);
            healthRepository.save(health);
        }

        LocalDateTime currentTime = LocalDateTime.now();
        long secondsElapsedInWindow = Duration.between(windowStartTime, currentTime).getSeconds();

        if (secondsElapsedInWindow >= 60) {
            windowStartTime = currentTime;
            requestsInCurrentWindow = 0;
            totalExcessRequests = 0; // Reset totalExcessRequests
        }

        requestsInCurrentWindow++;

        if (requestsInCurrentWindow > MAX_REQUESTS_PER_MINUTE) {
            totalExcessRequests += 1;

        if (excessRequestsInCurrentWindow > 0) {
            logger.info("Excess requests in current window: " + excessRequestsInCurrentWindow);

            if (excessRequestsInCurrentWindow > MAX_ALLOWED_DIFF) {
                logger.info("Excess requests in current window exceed the allowed limit. Initiating force crash.");
                health.setFlag(0);
                healthRepository.save(health);
                forceCrash();
            } else {
                excessRequestsInCurrentWindow = 0;
            }
        }

        Response response = new Response();
        response.setData(randomData);
        response.setTimestamp(LocalDateTime.now());
        response.setHealth(health);

        responseRepository.save(response);

        if (health != null) {
            int newRequestCount = health.getNumRequests() + 1;
            health.setNumRequests(newRequestCount);

            health.setDiff(excessRequestsInCurrentWindow);

            if (excessRequestsInCurrentWindow > MAX_ALLOWED_DIFF) {
                health.setFlag(0);
            } else {
                health.setFlag(1);
            }
            healthRepository.save(health);
        }

        // Broadcast operation to the other instance
        broadcastOperationToReplica(randomData);

        return response;
    }

    private void broadcastOperationToReplica(String data) {
        int otherInstancePort = (serverPort == 8081) ? 8082 : 8081;
        String replicaUrl = "http://localhost:" + otherInstancePort + "/response/replica-sync";

        try {
            restTemplate.postForEntity(replicaUrl, data, String.class);
            logger.info("Broadcasted operation to replica at {}", replicaUrl);
        } catch (Exception e) {
            logger.error("Failed to broadcast operation to replica: {}");
        }
    }

    // Method to process data received from replica
    public void processReplicaData(String data) {
        saveRandomResponseWithoutBroadcast(data);
    }

    // Save response without broadcasting to avoid infinite loops
    private void saveRandomResponseWithoutBroadcast(String randomData) {
        Health health = healthRepository.findFirstByOrderByIdDesc();

        if (health == null) {
            health = new Health();
            health.setNumRequests(0);
            health.setFlag(1);
            healthRepository.save(health);
        }

        Response response = new Response();
        response.setData(randomData);
        response.setTimestamp(LocalDateTime.now());
        response.setHealth(health);

        responseRepository.save(response);

        // Update health statistics if needed
    }

    private void forceCrash() {
        logger.error("System Crashed !! No further data processing.");

        // Mark the service as non-operational
        responseAlive = false;

        // Perform final check on /response
        try {
            ResponseEntity<String> responseHealthCheck = restTemplate.getForEntity("http://localhost:8080/response",
                    String.class);
            if (responseHealthCheck.getStatusCode().is2xxSuccessful()) {
                logger.info("Final check: /response is still alive but not operational.");
            } else {
                logger.error(
                        "Final check: /response is down. Status code: " + responseHealthCheck.getStatusCode().value());
            }
        } catch (HttpStatusCodeException e) {
            logger.error(
                    "Final check: /response returned an error: " + e.getStatusCode().value() + " " + e.getMessage());
            if (e.getStatusCode().value() == 404) {
                logger.error("/response returned 404 indicating it is not operational. Informing /health.");
                // Inform /health that /response is down
                informHealthEndpoint();
            }
        } catch (Exception e) {
            logger.error("Error during final check: " + e.get
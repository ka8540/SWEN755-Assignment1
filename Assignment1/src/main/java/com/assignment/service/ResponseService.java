package com.assignment.service;

import com.assignment.model.Health;
import com.assignment.model.Response;
import com.assignment.repository.HealthRepository;
import com.assignment.repository.ResponseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.http.ResponseEntity;

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
    private final int MAX_ALLOWED_DIFF = 30;

    private int requestsInCurrentWindow = 0;
    private LocalDateTime windowStartTime = LocalDateTime.now();
    private boolean responseAlive = true; // Track if /response is up

    private Random random = new Random();
    private RestTemplate restTemplate = new RestTemplate();

    private int excessRequestsInCurrentWindow = 0;

    @Value("${server.port}")
    private int serverPort;

    private int randomNumber = -1; // For random number generation in fault recovery

    @Scheduled(fixedRate = 60000) // This method runs every 120 seconds
    public void generateAndSendRandomRequests() {
        if (!responseAlive) {
            logger.info("System is down. No further requests will be sent to /response.");
            return; // Stop sending requests if /response is down
        }

        int requestsToSendInMinute = random.nextInt(101); // Random number of requests to send
        int intervalInMs = 60000 / Math.max(requestsToSendInMinute, 1); // Calculate interval

        logger.info("Requests to send in this minute: " + requestsToSendInMinute);

        // Reset the request counters at the start of the window
        requestsInCurrentWindow = 0; // Reset request counter for the new minute

        for (int i = 0; i < requestsToSendInMinute; i++) {
            // Calculate excess requests
            excessRequestsInCurrentWindow = requestsInCurrentWindow - MAX_REQUESTS_PER_MINUTE;
            logger.info("ExcessRequest:" + excessRequestsInCurrentWindow);
            boolean requestSuccessful = sendRandomDataToResponse();
            requestsInCurrentWindow++; // Increment the number of requests in the current window
            if (requestSuccessful) {
            } else {
                logger.info("Request to /response failed. Halting further requests and checking /response status.");
                checkResponseHealth();
                break; // Exit the loop and stop further processing after a failure
            }

            // Wait between requests
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
            ResponseEntity<Response> response = restTemplate.postForEntity(
                    "http://localhost:" + serverPort + "/response", randomData,
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

        // Reset window if 60 seconds have passed
        if (secondsElapsedInWindow >= 60) {
            windowStartTime = currentTime; // Reset the window start time
            requestsInCurrentWindow = 0; // Reset the requests counter
        }

        if (excessRequestsInCurrentWindow > 0) {
            logger.info("Excess requests in current window: " + excessRequestsInCurrentWindow);

            if (excessRequestsInCurrentWindow > MAX_ALLOWED_DIFF) {
                logger.info("Excess requests in current window exceed the allowed limit. Initiating force crash.");
                health.setFlag(0);
                healthRepository.save(health);
                forceCrash();
            }
        }

        Response response = new Response();
        response.setData(randomData);
        response.setTimestamp(LocalDateTime.now());
        response.setHealth(health);

        responseRepository.save(response);

        // Update health statistics
        int newRequestCount = health.getNumRequests() + 1;
        health.setNumRequests(newRequestCount);

        health.setDiff(excessRequestsInCurrentWindow);

        if (excessRequestsInCurrentWindow > MAX_ALLOWED_DIFF) {
            health.setFlag(0);
        } else {
            health.setFlag(1);
        }

        healthRepository.save(health);

        // Broadcast operation to the other instance
        broadcastOperationToReplica(randomData);

        return response;
    }

    private void broadcastOperationToReplica(String data) {
        int otherInstancePort = (serverPort == 8080) ? 8081 : 8080; // Use updated serverPort
        String replicaUrl = "http://localhost:" + otherInstancePort + "/response/replica-sync";

        try {
            restTemplate.postForEntity(replicaUrl, data, String.class);
            logger.info("Broadcasted operation to replica at {}", replicaUrl);
        } catch (Exception e) {
            logger.error("Failed to broadcast operation to replica: {}", e.getMessage());
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
        logger.info("Initiating comparison to determine which instance will crash...");
    
        // Get random number from the other instance
        int otherInstancePort = (serverPort == 8080) ? 8081 : 8080;
        int otherRandomNumber;
        do {
            // Generate a random number between 1 and 5 (inclusive)
            randomNumber = new Random().nextInt(5) + 1;
            logger.info("Instance on port {} generated random number: {}", serverPort, randomNumber);
    
            // Get random number from the other instance
            otherRandomNumber = getRandomNumberFromOtherInstance(otherInstancePort);
            logger.info("Other instance on port {} has random number: {}", otherInstancePort, otherRandomNumber);
    
        } while (randomNumber == otherRandomNumber);
    
        // Compare random numbers, the instance with the lower number will continue
        if (randomNumber < otherRandomNumber) {
            logger.info("This instance (port {}) will continue running. Other instance will go down.", serverPort);
            // Reset health flag to operational
            Health health = healthRepository.findFirstByOrderByIdDesc();
            if (health != null) {
                health.setFlag(1); // Continue operating
                healthRepository.save(health);
            }
        } else {
            logger.info("This instance (port {}) will stop processing requests.", serverPort);
    
            // Inform the health controller that this instance is down
            informHealthEndpoint(serverPort); // Pass the current instance's port
    
            // Mark the service as non-operational temporarily for this instance
            responseAlive = false;
    
            // Simulate restarting the instance after a delay (e.g., 10 seconds)
            scheduleInstanceRestart(serverPort, 10000); // Restart after 10 seconds
    
            // Reset window for the new instance before sending requests
            windowStartTime = LocalDateTime.now(); // Start a new request window after restart
            requestsInCurrentWindow = 0;
            excessRequestsInCurrentWindow = 0;
        }
    }
    

    private void scheduleInstanceRestart(int port, long delayMillis) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
    
            // Simulate the instance is back up
            logger.info("Instance on port {} has been restarted.", port);
    
            // Update internal state to mark the instance as alive
            responseAlive = true;
            logger.info("System is operational again on port {}", port);
    
            // Reset the counters
            excessRequestsInCurrentWindow = 0;
            requestsInCurrentWindow = 0;
            windowStartTime = LocalDateTime.now(); // Reset the window start time
            logger.info("Reset excessRequestsInCurrentWindow and requestsInCurrentWindow after instance restart.");
        }).start();
    }
    

    private Integer getRandomNumberFromOtherInstance(int port) {
        String url = "http://localhost:" + port + "/response/random-number";
        int attempts = 3; // Number of attempts to get a valid random number
        Integer otherRandomNumber = null;

        while (attempts-- > 0) {
            try {
                ResponseEntity<Integer> response = restTemplate.getForEntity(url, Integer.class);
                otherRandomNumber = response.getBody(); // Get the response body

                if (otherRandomNumber != null && otherRandomNumber >= 1) {
                    return otherRandomNumber; // Valid random number
                } else {
                    logger.warn("Received invalid random number from instance on port {}. Retrying...", port);
                }
            } catch (Exception e) {
                logger.error("Failed to get random number from instance on port {}: {}", port, e.getMessage());
            }

            // Wait briefly before retrying (optional)
            try {
                Thread.sleep(500); // 500 milliseconds delay between retries
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.warn("Failed to get valid random number from instance on port {}. Returning default value 1.", port);
        return 1; // Default to 1 if all attempts fail
    }

    private void informHealthEndpoint(int downedPort) {
        try {
            // Send a signal to /health indicating that the instance is down
            restTemplate.postForEntity("http://localhost:" + downedPort + "/health/instance-down?port=" + downedPort,
                    null, String.class);
            logger.info("Informed /health about instance on port {} being down.", downedPort);
        } catch (Exception e) {
            logger.error("Failed to inform /health: " + e.getMessage());
        }
    }

    public int getRandomNumber() {
        return randomNumber;
    }

    // Check if /response is alive
    private void checkResponseHealth() {
        try {
            ResponseEntity<String> responseHealthCheck = restTemplate.getForEntity(
                    "http://localhost:" + serverPort + "/response",
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

    public String generateRandomString() {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}

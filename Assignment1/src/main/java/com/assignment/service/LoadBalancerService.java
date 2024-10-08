package com.assignment.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LoadBalancerService {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerService.class);

    private List<ResponseServer> responseServers;  // List of response service instances
    private RestTemplate restTemplate;
    private AtomicInteger totalRequests;  // Total number of requests processed
    private int windowSize;    // Number of requests before considering shutdown
    private int threshold;     // Threshold after which a server should shut down
    private Random random;

    public LoadBalancerService() {
        restTemplate = new RestTemplate();
        random = new Random();
        totalRequests = new AtomicInteger(0);

        // Initialize the window size and threshold
        windowSize = 20;
        threshold = 60;

        // Initialize the list of response servers
        responseServers = new ArrayList<>();
        responseServers.add(new ResponseServer("http://localhost:8081/response", true));
        responseServers.add(new ResponseServer("http://localhost:8082/response", true));
    }

    // Method to handle incoming client requests
    public void handleRequest() {
        int currentRequestCount = totalRequests.incrementAndGet();

        int cycleLength = windowSize + threshold;  // Total length of the cycle
        int cyclePosition = currentRequestCount % cycleLength;

        // Start new servers at the beginning of each cycle
        if (cyclePosition == 1) {
            logger.info("Starting new servers at the beginning of the cycle.");
            startNewServers();
        }

        // After 'windowSize' requests, shut down one server
        if (cyclePosition == windowSize + 1) {
            logger.info("Threshold reached. Shutting down one server.");
            shutdownOneServer();
        }

        // Send request to available servers
        sendRequestToServers();
    }

    // Method to send requests to all servers that are 'up'
    private void sendRequestToServers() {
        for (ResponseServer server : responseServers) {
            if (server.isUp()) {
                try {
                    // Send a POST request to the server's /response endpoint
                    restTemplate.postForEntity(server.getUrl(), null, String.class);
                    logger.info("Request sent to {}", server.getUrl());
                } catch (Exception e) {
                    logger.error("Error sending request to {}: {}", server.getUrl(), e.getMessage());
                }
            } else {
                logger.info("Server {} is down. Skipping.", server.getUrl());
            }
        }
    }

    // Method to shut down one server based on random numbers
    private void shutdownOneServer() {
        // Generate random numbers between 1 and 5 for each server
        int randomNumber1 = random.nextInt(5) + 1;
        int randomNumber2 = random.nextInt(5) + 1;

        ResponseServer server1 = responseServers.get(0);
        ResponseServer server2 = responseServers.get(1);

        ResponseServer serverToShutdown;

        // Log the random numbers
        logger.info("Random numbers generated: Server1={}, Server2={}", randomNumber1, randomNumber2);

        // Decide which server to shut down based on the random numbers
        if (randomNumber1 > randomNumber2) {
            serverToShutdown = server1;
        } else if (randomNumber2 > randomNumber1) {
            serverToShutdown = server2;
        } else {
            // If both random numbers are equal, randomly select a server to shut down
            serverToShutdown = random.nextBoolean() ? server1 : server2;
            logger.info("Random numbers are equal. Randomly selected server to shut down.");
        }

        logger.info("Server chosen to shut down: {}", serverToShutdown.getUrl());

        if (serverToShutdown.isUp()) {
            try {
                // Send a request to set the server's isAlive status to false
                restTemplate.postForEntity(serverToShutdown.getUrl() + "/setAlive?isAlive=false", null, String.class);
                serverToShutdown.setUp(false);  // Update the server's status locally
                logger.info("Server {} is shut down.", serverToShutdown.getUrl());
            } catch (Exception e) {
                logger.error("Error shutting down server {}: {}", serverToShutdown.getUrl(), e.getMessage());
            }
        } else {
            logger.info("Server {} is already down.", serverToShutdown.getUrl());
        }
    }

    // Method to start any servers that are currently down
    private void startNewServers() {
        for (ResponseServer server : responseServers) {
            if (!server.isUp()) {
                try {
                    // Send a request to set the server's isAlive status to true
                    restTemplate.postForEntity(server.getUrl() + "/setAlive?isAlive=true", null, String.class);
                    server.setUp(true);  // Update the server's status locally
                    logger.info("Server {} is started.", server.getUrl());
                } catch (Exception e) {
                    logger.error("Error starting server {}: {}", server.getUrl(), e.getMessage());
                }
            } else {
                logger.info("Server {} is already up.", server.getUrl());
            }
        }
    }

    // Inner class representing a response server
    private class ResponseServer {
        private String url;    // The base URL of the response service
        private boolean isUp;  // The status of the server (up or down)

        public ResponseServer(String url, boolean isUp) {
            this.url = url;
            this.isUp = isUp;
        }

        // Getter for the URL
        public String getUrl() {
            return url;
        }

        // Getter for the server status
        public boolean isUp() {
            return isUp;
        }

        // Setter for the server status
        public void setUp(boolean isUp) {
            this.isUp = isUp;
        }
    }
}

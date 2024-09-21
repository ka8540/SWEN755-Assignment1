package com.assignment.service;

import com.assignment.model.Health;
import com.assignment.model.Response;
import com.assignment.repository.HealthRepository;
import com.assignment.repository.ResponseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private int totalExcessRequests = 0;
    private LocalDateTime windowStartTime = LocalDateTime.now();

    private Random random = new Random();

    @Scheduled(fixedRate = 60000)
    public void generateAndSendRandomRequests() {
        int requestsToSendInMinute = random.nextInt(101);
        int intervalInMs = 60000 / Math.max(requestsToSendInMinute, 1);

        logger.info("Requests to send in this minute: " + requestsToSendInMinute);

        for (int i = 0; i < requestsToSendInMinute; i++) {
            saveRandomResponse();
            try {
                Thread.sleep(intervalInMs);
            } catch (InterruptedException e) {
                logger.error("Interrupted during sleep: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Number of requests processed in this minute: " + requestsToSendInMinute);
    }

    public Response saveRandomResponse() {
        Health health = healthRepository.findFirstByOrderByIdDesc();

        if (health == null) {
            health = new Health();
            health.setNumRequests(0);
            health.setFlag(1);
            healthRepository.save(health);
        }

        if (health.getFlag() != null && health.getFlag() == 0) {
            throw new RuntimeException("System crashed. No further requests will be processed.");
        }

        LocalDateTime currentTime = LocalDateTime.now();
        long secondsElapsedInWindow = Duration.between(windowStartTime, currentTime).getSeconds();

        if (secondsElapsedInWindow >= 60) {
            windowStartTime = currentTime;
            requestsInCurrentWindow = 0;
            // Reset totalExcessRequests at the start of a new window if desired
            totalExcessRequests = 0;
        }

        requestsInCurrentWindow++;

        if (requestsInCurrentWindow > MAX_REQUESTS_PER_MINUTE) {
            // Increment totalExcessRequests by 1 for each excess request
            totalExcessRequests += 1;

            logger.info("Excess requests in current window: " + (requestsInCurrentWindow - MAX_REQUESTS_PER_MINUTE));
            logger.info("Total excess requests: " + totalExcessRequests);

            if (totalExcessRequests > MAX_ALLOWED_DIFF) {
                health.setFlag(0);
                healthRepository.save(health);
                forceCrash();
            }
        }

        String randomData = generateRandomString();
        logger.info("Generated Data: " + randomData + " | Timestamp: " + currentTime);

        Response response = new Response();
        response.setData(randomData);
        response.setTimestamp(LocalDateTime.now());
        response.setHealth(health);

        responseRepository.save(response);

        if (health != null) {
            int newRequestCount = health.getNumRequests() + 1;
            health.setNumRequests(newRequestCount);

            int diff = totalExcessRequests;
            health.setDiff(diff);

            if (diff > MAX_ALLOWED_DIFF) {
                health.setFlag(0);
            } else {
                health.setFlag(1);
            }
            healthRepository.save(health);
        }

        return response;
    }

    private void forceCrash() {
        logger.error("System Crashed !!");
        System.exit(1);
    }

    private String generateRandomString() {
        int leftLimit = 97;
        int rightLimit = 122;
        int targetStringLength = 10;
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}

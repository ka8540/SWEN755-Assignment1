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

    @Autowired
    private HealthRepository healthRepository;

    @Autowired
    private ResponseRepository responseRepository;

    private final Logger logger = LoggerFactory.getLogger(ResponseService.class);

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

        logger.info("===== Minute Window Start: " + windowStartTime + " =====");
        logger.info("[" + LocalDateTime.now() + "] Requests to send in this minute: " + requestsToSendInMinute);

        for (int i = 0; i < requestsToSendInMinute; i++) {
            saveRandomResponse();
            try {
                Thread.sleep(intervalInMs);
            } catch (InterruptedException e) {
                logger.error("[" + LocalDateTime.now() + "] Interrupted during sleep: " + e.getMessage());
                Thread.currentThread().interrupt(); 
                return;
            } catch (Exception e) {
                logger.error("[" + LocalDateTime.now() + "] Failed to process request: " + e.getMessage());
                if (e.getMessage().contains("System crashed")) {
                    forceCrash();
                }
            }
        }


        LocalDateTime currentTime = LocalDateTime.now();
        long secondsElapsedInWindow = Duration.between(windowStartTime, currentTime).getSeconds();
        if (secondsElapsedInWindow >= 60) {
            windowStartTime = currentTime;
            requestsInCurrentWindow = 0; 
            totalExcessRequests = 0; 
        }
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
            logger.error("[" + LocalDateTime.now() + "] System crashed. No further requests will be processed.");
            throw new RuntimeException("System crashed. No further requests will be processed.");
        }


        String randomData = generateRandomString();
        Response response = new Response();
        response.setData(randomData);
        response.setTimestamp(LocalDateTime.now());
        response.setHealth(health);

        responseRepository.save(response);

        int newRequestCount = health.getNumRequests() + 1;
        health.setNumRequests(newRequestCount);

        
        requestsInCurrentWindow++;
        if (requestsInCurrentWindow > MAX_REQUESTS_PER_MINUTE) {
            totalExcessRequests++;
        }

        
        int diff = totalExcessRequests;
        health.setDiff(diff);
        if (diff > MAX_ALLOWED_DIFF) {
            health.setFlag(0);
            logger.info("[" + LocalDateTime.now() + "] Total Excess Requests: " + totalExcessRequests + " | Difference: " + diff + " | Flag set to 0 - System will crash");
            forceCrash(); 
        } else {
            health.setFlag(1);
            logger.info("[" + LocalDateTime.now() + "] Request processed: " + randomData + " | Total Excess Requests: " + totalExcessRequests + " | Flag set to 1");
        }

        healthRepository.save(health);
        return response;
    }

    private void forceCrash() {
        logger.error("[" + LocalDateTime.now() + "] System Crashed !!");
        System.exit(1); 
    }

    private String generateRandomString() {
        int leftLimit = 97; 
        int rightLimit = 122; 
        int targetStringLength = 10;

        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}

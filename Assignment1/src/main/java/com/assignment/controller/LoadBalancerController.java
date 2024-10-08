package com.assignment.controller;

import com.assignment.service.LoadBalancerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/loadbalancer")
public class LoadBalancerController {

    @Autowired
    private LoadBalancerService loadBalancerService;

    // Endpoint to handle incoming client requests
    @PostMapping
    public void handleClientRequest() {
        loadBalancerService.handleRequest();
    }
}

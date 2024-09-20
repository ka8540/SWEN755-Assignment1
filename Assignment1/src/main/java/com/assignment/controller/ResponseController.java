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

    @PostMapping
    public ResponseEntity<Response> createResponse() {
        Response response = responseService.saveRandomResponse();
        return ResponseEntity.ok(response);
    }
}

package com.assignment.controller;

import com.assignment.model.Client;
import com.assignment.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/client")
public class ClientController {

    @Autowired
    private ClientRepository clientRepository;

    @PostMapping
    public ResponseEntity<Client> createClientData(@RequestBody String data) {
        Client client = new Client();
        client.setData(data);
        clientRepository.save(client);
        return ResponseEntity.ok(client);
    }

    @GetMapping
    public ResponseEntity<Client> getClientData() {
        Client client = clientRepository.findFirstByOrderByIdDesc();
        return ResponseEntity.ok(client);
    }
}

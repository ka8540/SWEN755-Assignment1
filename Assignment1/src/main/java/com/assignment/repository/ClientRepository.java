package com.assignment.repository;

import com.assignment.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Client findFirstByOrderByIdDesc();
}

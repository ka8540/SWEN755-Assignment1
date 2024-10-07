package com.assignment.repository;

import com.assignment.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    // Custom query to find the most recent client data
    Client findFirstByOrderByIdDesc();
}

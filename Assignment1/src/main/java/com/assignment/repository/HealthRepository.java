package com.assignment.repository;

import com.assignment.model.Health;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthRepository extends JpaRepository<Health, Long> {
    Health findFirstByOrderByIdDesc();
}

package com.assignment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "response")
@Getter
@Setter
public class Response {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String data;

    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "health_id", nullable = false)
    @JsonIgnore
    private Health health;
}

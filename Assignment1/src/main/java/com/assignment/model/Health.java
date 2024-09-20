package com.assignment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "health")
@Getter
@Setter
public class Health {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "health")
    private List<Response> responses;

    @Column(name = "num_requests")
    private int numRequests;

    private int diff;

    private Integer flag;
}

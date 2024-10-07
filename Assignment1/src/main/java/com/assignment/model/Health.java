package com.assignment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "health")
@Getter
@Setter
public class Health {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "health", fetch = FetchType.EAGER) // Change fetch type to LAZY
    @JsonIgnore // Prevent serialization of the lazy-loaded collection
    private List<Response> responses;

    @Column(name = "num_requests")
    private int numRequests;

    private int diff;

    private Integer flag;
}

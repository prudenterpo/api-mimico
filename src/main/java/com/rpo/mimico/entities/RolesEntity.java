package com.rpo.mimico.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class RolesEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private  String name;

    private String description;

    @ManyToMany(mappedBy = "roles")
    private Set<UsersEntity> users = new HashSet<>();

    public RolesEntity() {}

    public RolesEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }
}

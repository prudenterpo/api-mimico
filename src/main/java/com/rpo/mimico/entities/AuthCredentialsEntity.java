package com.rpo.mimico.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "auth_credentials")
@Getter
@Setter
public class AuthCredentialsEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "last_session_id")
    private String lastSessionId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UsersEntity user;
}

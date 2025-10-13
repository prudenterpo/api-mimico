package com.rpo.mimico.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "match_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false, unique = true)
    private MatchEntity match;

    @Column(name = "team_a_position", nullable = false)
    private Integer teamAPosition;

    @Column(name = "team_b_position", nullable = false)
    private Integer teamBPosition;

    @Column(name = "current_team", nullable = false, length = 1)
    private Character currentTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_mime_player_id")
    private UserEntity currentMimePlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_word_id")
    private WordEntity currentWord;

    @Column(name = "round_expires_at")
    private LocalDateTime roundExpiresAt;

    @Column(name = "is_paused", nullable = false)
    private Boolean isPaused;
}
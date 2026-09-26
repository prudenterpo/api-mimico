package com.rpo.mimico.entities;

import com.rpo.mimico.domain.PauseReason;
import com.rpo.mimico.domain.RoundState;
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

    @Column(name = "current_team", length = 1)
    private Character currentTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_mime_player_id")
    private UserEntity currentMimePlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_word_id")
    private WordEntity currentWord;

    @Enumerated(EnumType.STRING)
    @Column(name = "round_state", length = 40)
    private RoundState roundState;

    @Column(name = "round_expires_at")
    private LocalDateTime roundExpiresAt;

    @Column(name = "is_paused", nullable = false)
    private Boolean isPaused;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "pause_reason", length = 40)
    private PauseReason pauseReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disconnected_user_id")
    private UserEntity disconnectedUser;

    @Column(name = "reconnect_deadline")
    private LocalDateTime reconnectDeadline;

    @Column(name = "remaining_round_seconds_on_pause")
    private Integer remainingRoundSecondsOnPause;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sorteio_player_a_id")
    private UserEntity sorteioPlayerA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sorteio_player_b_id")
    private UserEntity sorteioPlayerB;

    @Column(name = "sorteio_roll_a")
    private Integer sorteioRollA;

    @Column(name = "sorteio_roll_b")
    private Integer sorteioRollB;
}
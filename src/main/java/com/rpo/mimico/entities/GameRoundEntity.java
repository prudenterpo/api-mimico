package com.rpo.mimico.entities;

import com.rpo.mimico.domain.RoundResolution;
import com.rpo.mimico.domain.RoundState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_rounds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameRoundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchEntity match;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "round_state", nullable = false, length = 40)
    private RoundState roundState;

    @Column(name = "current_team", nullable = false, length = 1)
    private Character currentTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mime_player_id", nullable = false)
    private MatchPlayerEntity mimePlayer;

    @Column(name = "dice_value")
    private Integer diceValue;

    @Column(name = "landing_tile")
    private Integer landingTile;

    @Column(name = "is_special_tile", nullable = false)
    private Boolean specialTile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_word_id")
    private WordEntity selectedWord;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RoundResolution resolution;
}

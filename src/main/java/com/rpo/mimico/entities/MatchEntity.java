package com.rpo.mimico.entities;

import com.rpo.mimico.domain.FinishReason;
import com.rpo.mimico.domain.MatchStatus;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private GameTableEntity table;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 32)
    private MatchStatus matchStatus;

    @Column(name = "winner_team", length = 1)
    private Character winnerTeam;

    @Enumerated(EnumType.STRING)
    @Column(name = "finish_reason", length = 32)
    private FinishReason finishReason;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Version
    @Column(name = "version")
    private Integer version;
}

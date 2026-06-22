package com.linktracker.scrapper.infrastructure.memory.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "link_update_checkpoints")
@Getter
@Setter
@NoArgsConstructor
public class LinkUpdateCheckpointEntity {

    @Id
    @Column(name = "link_id")
    private Long linkId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "link_id", nullable = false)
    private LinkEntity link;

    @Column(name = "last_seen_external_updated_at", nullable = false)
    private Instant lastSeenExternalUpdatedAt;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;
}

package backend.academy.linktracker.scrapper.infrastructure.memory.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "link_update_outbox")
@Getter
@Setter
@NoArgsConstructor
public class LinkUpdateOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "payload_id", nullable = false)
    private Long payloadId;

    @Column(name = "payload_url", nullable = false)
    private String payloadUrl;

    @Column(name = "payload_description", nullable = false)
    private String payloadDescription;

    @Column(name = "payload_author", nullable = false)
    private String payloadAuthor;

    @Column(name = "payload_tg_chat_ids", nullable = false)
    private String payloadTgChatIds;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}

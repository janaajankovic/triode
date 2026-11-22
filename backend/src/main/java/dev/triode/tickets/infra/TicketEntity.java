package dev.triode.tickets.infra;

import dev.triode.tickets.domain.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA mapping of the {@code tickets} table (V2 and V3 migrations). Maps to and from the domain {@code Ticket}. */
@Entity
@Table(name = "tickets")
class TicketEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false, updatable = false, length = 255)
    private String subject;

    @Column(nullable = false, updatable = false, length = 10_000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Null until the first insert, so Spring Data treats the entity as new and persists it. */
    @Version
    @Column(nullable = false)
    private Long version;

    /** Null for tickets submitted without a key (V3 migration). */
    @Column(name = "idempotency_key", updatable = false, length = 255)
    private String idempotencyKey;

    /** {@code CHAR(64)} in the schema; without the type code, schema validation expects {@code varchar}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_fingerprint", updatable = false, length = 64)
    private String requestFingerprint;

    protected TicketEntity() {}

    TicketEntity(
            UUID id,
            UUID customerId,
            String subject,
            String body,
            TicketStatus status,
            Instant createdAt,
            String idempotencyKey,
            String requestFingerprint) {
        this.id = id;
        this.customerId = customerId;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.createdAt = createdAt;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    UUID getId() {
        return id;
    }

    UUID getCustomerId() {
        return customerId;
    }

    String getSubject() {
        return subject;
    }

    String getBody() {
        return body;
    }

    TicketStatus getStatus() {
        return status;
    }

    void setStatus(TicketStatus status) {
        this.status = status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Long getVersion() {
        return version;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    String getRequestFingerprint() {
        return requestFingerprint;
    }
}

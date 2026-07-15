package com.talentos.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditable mapped superclass for all mutable TalentOS domain entities.
 *
 * <p>Extends {@link BaseEntity} (UUID PK) and adds:
 * <ul>
 *   <li><b>createdAt</b>  — populated by {@code @CreatedDate}  on INSERT</li>
 *   <li><b>updatedAt</b>  — populated by {@code @LastModifiedDate} on INSERT and UPDATE</li>
 *   <li><b>createdBy</b>  — populated by {@code @CreatedBy} from the {@link org.springframework.data.domain.AuditorAware} bean</li>
 *   <li><b>updatedBy</b>  — populated by {@code @LastModifiedBy} from the {@link org.springframework.data.domain.AuditorAware} bean</li>
 *   <li><b>deletedAt</b>  — set manually by the service layer on soft-delete; NULL means record is active</li>
 * </ul>
 *
 * <p>Soft-delete pattern (ERD section 10):
 * <ul>
 *   <li>The owning entity adds an {@code isDeleted} boolean column (default false).</li>
 *   <li>When soft-deleted, the service sets {@code isDeleted = true} and {@code deletedAt = Instant.now()}.</li>
 *   <li>All standard repositories filter on {@code WHERE is_deleted = false} via Hibernate
 *       {@code @SQLRestriction} on the concrete entity class — not on this superclass,
 *       because not every entity uses soft-delete (e.g., resume_analysis, activity_logs).</li>
 * </ul>
 *
 * <p>Timestamps are stored as {@code TIMESTAMP WITH TIME ZONE} (UTC) in PostgreSQL
 * using {@link Instant} (always UTC, no JVM timezone dependency).
 *
 * <p>Audit columns match ERD section 9:
 * <pre>
 *   created_at  TIMESTAMPTZ  — Spring @CreatedDate
 *   updated_at  TIMESTAMPTZ  — Spring @LastModifiedDate
 *   created_by  UUID         — Spring @CreatedBy (nullable for system/anonymous events)
 *   updated_by  UUID         — Spring @LastModifiedBy (nullable)
 *   deleted_at  TIMESTAMPTZ  — service-managed soft-delete timestamp
 * </pre>
 *
 * <p>This class contains <strong>no business logic, no repositories, no services,
 * and no controllers</strong> — it is a pure data carrier for the persistence layer.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class AuditableEntity extends BaseEntity {

    // -------------------------------------------------------------------------
    // Timestamps — Spring Data JPA Auditing
    // -------------------------------------------------------------------------

    /**
     * Timestamp of record creation. Set once on INSERT; never updated.
     * Stored as {@code TIMESTAMP WITH TIME ZONE} (UTC).
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    /**
     * Timestamp of the last modification. Updated on every MERGE/UPDATE.
     * Also populated on INSERT (equals {@link #createdAt} initially).
     * Stored as {@code TIMESTAMP WITH TIME ZONE} (UTC).
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    // Actor tracking — resolved via AuditorAware<UUID>
    // -------------------------------------------------------------------------

    /**
     * UUID of the user who created this record.
     * Nullable: system-triggered events (e.g., scheduled jobs) may have no actor.
     */
    @CreatedBy
    @Column(name = "created_by", updatable = false, columnDefinition = "uuid")
    private UUID createdBy;

    /**
     * UUID of the user who last modified this record.
     * Nullable: matches the same nullable contract as {@link #createdBy}.
     */
    @LastModifiedBy
    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;

    // -------------------------------------------------------------------------
    // Soft-delete timestamp — service-managed
    // -------------------------------------------------------------------------

    /**
     * Timestamp at which this record was logically deleted.
     * {@code null} means the record is active (not deleted).
     *
     * <p>The companion {@code isDeleted} boolean and {@code deletedBy} UUID
     * are declared on each concrete entity that participates in soft-delete,
     * because not all entities support soft-delete (e.g., {@code resume_analysis},
     * {@code activity_logs} are excluded per ERD section 10).
     *
     * <p>Set exclusively by the service layer via
     * {@code entity.setDeletedAt(Instant.now())} — never auto-populated by JPA.
     */
    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant deletedAt;

    // -------------------------------------------------------------------------
    // Convenience helpers (no business logic — pure state queries)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@link #deletedAt} has been set, indicating that
     * the record has been logically deleted.
     *
     * <p>Prefer the concrete entity's {@code isDeleted} flag for repository-level
     * filtering; this helper is provided for in-memory checks within the service layer.
     */
    public boolean isSoftDeleted() {
        return deletedAt != null;
    }
}

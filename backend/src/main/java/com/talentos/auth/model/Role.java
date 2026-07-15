package com.talentos.auth.model;

import com.talentos.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * JPA entity representing the {@code roles} lookup table.
 *
 * <p>The {@code roles} table is a reference/lookup table that stores the four
 * system roles known to TalentOS: {@code ADMIN}, {@code RECRUITER},
 * {@code HIRING_MANAGER}, and {@code CANDIDATE}.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><strong>Lookup table, not a Java enum column:</strong> The ERD defines
 *       {@code roles} as a first-class table with a UUID PK. This allows future
 *       role expansion and description updates without schema changes.</li>
 *   <li><strong>No soft-delete:</strong> Per ERD section 10, {@code roles} is
 *       system-seeded reference data that is never deleted. Accordingly this
 *       entity extends {@link BaseEntity} (UUID PK only) rather than
 *       {@link com.talentos.shared.entity.AuditableEntity}. Timestamps are
 *       still tracked via lightweight {@link CreationTimestamp} /
 *       {@link UpdateTimestamp} — without requiring the full
 *       {@code AuditingEntityListener} overhead for a static lookup table.</li>
 *   <li><strong>Enum-typed {@code name}:</strong> The {@code name} column is
 *       stored as a {@code VARCHAR} in PostgreSQL (matching the ERD CHECK
 *       constraint) and mapped to the {@link RoleName} Java enum via
 *       {@link EnumType#STRING}. This prevents arbitrary string insertion while
 *       keeping the DB type flexible.</li>
 *   <li><strong>No Role-Permission table:</strong> TalentOS MVP does not include
 *       a permissions entity. Access control is enforced entirely through Spring
 *       Security method-level {@code @PreAuthorize} annotations keyed on the
 *       {@link RoleName} values.</li>
 * </ul>
 *
 * <h2>Indexes</h2>
 * <ul>
 *   <li>Primary key index on {@code id} — auto-created by JPA.</li>
 *   <li>Unique index on {@code name} — prevents duplicate role names and serves
 *       as the fast lookup path when resolving a role by name at signup.</li>
 * </ul>
 *
 * <h2>Relationships</h2>
 * <pre>
 *   roles  1 ──── N  users   (users.role_id → roles.id  ON DELETE RESTRICT)
 * </pre>
 * The {@code users} side of this relationship is declared on the {@code User}
 * entity (Phase 3) to keep Role free of reverse references.
 */
@Entity
@Table(
        name = "roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_roles_name", columnNames = "name")
        },
        indexes = {
                @Index(name = "idx_roles_name", columnList = "name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Role extends BaseEntity {

    // -------------------------------------------------------------------------
    // Core identity
    // -------------------------------------------------------------------------

    /**
     * The role identifier — one of the four fixed {@link RoleName} values.
     *
     * <p>Stored as a {@code VARCHAR} in PostgreSQL with a unique constraint.
     * Mapped via {@link EnumType#STRING} so the column value is the enum name
     * (e.g., {@code "RECRUITER"}), not the ordinal.
     *
     * <p>Bean Validation: {@code @NotNull} prevents a null role name from ever
     * reaching the database. The {@link EnumType#STRING} mapping handles the
     * value constraint at the JPA layer.
     */
    @NotNull(message = "Role name is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 30)
    private RoleName name;

    /**
     * Human-readable description of the role's purpose and capabilities.
     *
     * <p>Optional but recommended. Populated by the Flyway seed migration.
     * Max 255 chars — sufficient for a one-sentence description.
     */
    @Size(max = 255, message = "Role description must not exceed 255 characters")
    @Column(name = "description", length = 255)
    private String description;

    // -------------------------------------------------------------------------
    // Timestamps — lightweight Hibernate annotations (no AuditingEntityListener)
    // -------------------------------------------------------------------------

    /**
     * Timestamp of row insertion. Set once by Hibernate on INSERT.
     * Stored as {@code TIMESTAMP WITH TIME ZONE} (UTC).
     *
     * <p>Uses {@link CreationTimestamp} rather than Spring's {@code @CreatedDate}
     * because {@code roles} is a static lookup table that does not need the full
     * Spring Data auditing infrastructure (no {@code created_by} tracking needed).
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    /**
     * Timestamp of the last row modification. Updated by Hibernate on every UPDATE.
     * Stored as {@code TIMESTAMP WITH TIME ZONE} (UTC).
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    // Convenience factory (for tests and Flyway-independent seeding)
    // -------------------------------------------------------------------------

    /**
     * Creates a minimal, transient {@link Role} instance from a {@link RoleName}.
     *
     * <p>Intended for use in unit tests or bootstrap contexts where a fully
     * persisted role is not yet available. This method does <em>not</em> persist
     * the role — the caller is responsible for saving it via a repository.
     *
     * @param roleName the role identifier
     * @return a new, unpersisted {@code Role} with no ID and no description
     */
    public static Role of(RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        return role;
    }

    /**
     * Creates a transient {@link Role} with both a name and a description.
     *
     * @param roleName    the role identifier
     * @param description human-readable description
     * @return a new, unpersisted {@code Role}
     */
    public static Role of(RoleName roleName, String description) {
        Role role = new Role();
        role.setName(roleName);
        role.setDescription(description);
        return role;
    }
}

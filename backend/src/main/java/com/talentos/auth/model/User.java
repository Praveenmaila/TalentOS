package com.talentos.auth.model;

import com.talentos.shared.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

/**
 * JPA entity representing the {@code users} table — the central identity
 * record for every person in the TalentOS platform.
 *
 * <h2>Responsibility</h2>
 * <p>Holds authentication credentials and account-level metadata. Every user
 * has exactly one {@link Role}. Role-specific profile data (recruiter title,
 * candidate headline, etc.) lives in separate profile-extension entities that
 * reference this entity via a one-to-one foreign key.
 *
 * <h2>Inheritance</h2>
 * <p>Extends {@link AuditableEntity}, which provides:
 * <ul>
 *   <li>{@code id}         — UUID v4 primary key (from {@link com.talentos.shared.entity.BaseEntity})</li>
 *   <li>{@code createdAt}  — {@code @CreatedDate}, set on INSERT</li>
 *   <li>{@code updatedAt}  — {@code @LastModifiedDate}, set on INSERT + UPDATE</li>
 *   <li>{@code createdBy}  — {@code @CreatedBy}, nullable UUID of the acting user</li>
 *   <li>{@code updatedBy}  — {@code @LastModifiedBy}, nullable UUID</li>
 *   <li>{@code deletedAt}  — soft-delete timestamp, service-managed</li>
 * </ul>
 *
 * <h2>Soft Delete</h2>
 * <p>Per ERD section 10, users support soft delete to preserve account history,
 * applications, and activity logs. Three fields implement the pattern:
 * <ul>
 *   <li>{@code isDeleted}  — boolean flag (default {@code false}); repository filter</li>
 *   <li>{@code deletedAt}  — timestamp (inherited from {@link AuditableEntity})</li>
 *   <li>{@code deletedBy}  — UUID of the admin/system that performed the delete</li>
 * </ul>
 * <p>The Hibernate 6 {@code @SQLRestriction("is_deleted = false")} annotation
 * ensures all derived and specification-based queries automatically exclude
 * deleted records without manual filtering in the service layer. Admin endpoints
 * bypass this restriction via {@code @Query} with {@code nativeQuery = true}.
 *
 * <h2>Indexes (per ERD §8)</h2>
 * <ul>
 *   <li>PK index on {@code id}                       — auto-created by JPA</li>
 *   <li>Unique index on {@code email}                — one account per address</li>
 *   <li>Performance index on {@code role_id}         — filter users by role</li>
 *   <li>Performance index on {@code (is_deleted, is_active)} — global active-user filter</li>
 * </ul>
 *
 * <h2>Relationships</h2>
 * <pre>
 *   users  N:1  roles          (users.role_id → roles.id   ON DELETE RESTRICT)
 *   users  1:1  recruiters     (recruiters.user_id → users.id ON DELETE CASCADE) — declared on Recruiter
 *   users  1:1  hiring_managers                                                  — declared on HiringManager
 *   users  1:1  candidates                                                       — declared on Candidate
 *   users  1:N  notifications  (notifications.recipient_user_id → users.id)      — declared on Notification
 *   users  1:N  activity_logs  (activity_logs.actor_user_id → users.id)          — declared on ActivityLog
 * </pre>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>{@code phone} is intentionally absent from this entity. Per the ERD,
 *       phone numbers live on the profile-extension tables ({@code recruiters},
 *       {@code hiring_managers}) — not on the base user record.</li>
 *   <li>{@code passwordHash} stores only the BCrypt-hashed output. The raw
 *       password is never persisted and never appears in any entity field.</li>
 *   <li>Email is stored lowercase (enforced by the service layer before save)
 *       and looked up case-insensitively per the validation rules.</li>
 * </ul>
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        },
        indexes = {
                // Fast role-based user lookup (admin user management)
                @Index(name = "idx_users_role_id",          columnList = "role_id"),
                // Global active-user filter applied on virtually every query
                @Index(name = "idx_users_deleted_active",   columnList = "is_deleted, is_active")
        }
)
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class User extends AuditableEntity {

    // -------------------------------------------------------------------------
    // Role relationship (N:1 — many users share one role)
    // -------------------------------------------------------------------------

    /**
     * The system role assigned to this user.
     *
     * <p>FK: {@code users.role_id → roles.id  ON DELETE RESTRICT}.
     * RESTRICT is intentional — a role cannot be dropped while any user holds it.
     *
     * <p>Fetched {@link FetchType#EAGER} because the role is required in virtually
     * every security context resolution and is a tiny, cacheable lookup record.
     * The overhead of an extra join is negligible compared to the complexity
     * of lazy-loading role data inside {@code AuditorAware} or {@code UserDetails}.
     */
    @NotNull(message = "User role is required")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "role_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_users_role_id")
    )
    private Role role;

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    /**
     * User's given (first) name.
     *
     * <p>Validation: 1–50 characters; letters, spaces, hyphens, apostrophes
     * (per {@code docs/validation.md} §1).
     * Trimmed before persistence by the service layer.
     */
    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    @Pattern(
            regexp = "^[a-zA-Z\\s'\\-]{1,50}$",
            message = "First name may only contain letters, spaces, hyphens, and apostrophes"
    )
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    /**
     * User's family (last) name.
     *
     * <p>Same validation rules as {@link #firstName}.
     */
    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    @Pattern(
            regexp = "^[a-zA-Z\\s'\\-]{1,50}$",
            message = "Last name may only contain letters, spaces, hyphens, and apostrophes"
    )
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    // -------------------------------------------------------------------------
    // Credentials
    // -------------------------------------------------------------------------

    /**
     * Email address used as the login identifier.
     *
     * <p>Must be unique across the platform. Stored lowercase.
     * Max 254 chars per RFC 5321.
     *
     * <p>Bean Validation enforces the format at the DTO/request layer;
     * the {@code @Email} annotation here provides a second defence at the
     * entity layer for programmatic construction paths.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(min = 5, max = 254, message = "Email must be between 5 and 254 characters")
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    /**
     * BCrypt-hashed password. The raw password is <strong>never</strong> stored.
     *
     * <p>Column length 255 accommodates the standard 60-character BCrypt output
     * with room for algorithm prefix changes (e.g., Argon2 migration).
     * Not exposed in any DTO, response, or log.
     */
    @NotBlank(message = "Password hash is required")
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // -------------------------------------------------------------------------
    // Optional profile fields
    // -------------------------------------------------------------------------

    /**
     * Absolute URL to the user's profile avatar image.
     *
     * <p>Nullable — set post-signup via profile update. Must begin with
     * {@code http://} or {@code https://} when provided.
     * Max 2048 chars to accommodate long CDN URLs.
     */
    @Pattern(
            regexp = "^https?://.+",
            message = "Avatar URL must be a valid absolute HTTP/HTTPS URL"
    )
    @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters")
    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    // -------------------------------------------------------------------------
    // Account status
    // -------------------------------------------------------------------------

    /**
     * Whether this account is currently active.
     *
     * <p>Deactivated accounts ({@code isActive = false}) cannot authenticate;
     * login attempts return HTTP 401 per the validation rules.
     * Defaults to {@code true} on signup (account is active immediately).
     *
     * <p>NOT NULL in the database (ERD §7). Managed exclusively by the
     * service/admin layer — never set by the user themselves.
     */
    @NotNull(message = "Account active status is required")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    // -------------------------------------------------------------------------
    // Soft delete (complementing deletedAt inherited from AuditableEntity)
    // -------------------------------------------------------------------------

    /**
     * Logical deletion flag.
     *
     * <p>When {@code true}, this record is considered deleted. All standard
     * repository queries exclude rows where {@code is_deleted = true} via the
     * class-level {@code @SQLRestriction("is_deleted = false")} annotation.
     *
     * <p>Set together with {@link AuditableEntity#getDeletedAt()} and
     * {@link #deletedBy} by the service layer in a single transaction.
     * Defaults to {@code false}.
     */
    @NotNull(message = "Deleted flag is required")
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = Boolean.FALSE;

    /**
     * UUID of the admin or system actor who performed the soft-delete.
     *
     * <p>Nullable — set only when {@link #isDeleted} transitions to {@code true}.
     * Complements {@link AuditableEntity#getDeletedAt()} (the timestamp)
     * to form the complete soft-delete audit trail per ERD §10.
     */
    @Column(name = "deleted_by", columnDefinition = "uuid")
    private UUID deletedBy;
}

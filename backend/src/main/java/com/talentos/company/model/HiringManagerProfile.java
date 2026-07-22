package com.talentos.company.model;

import com.talentos.shared.entity.AuditableEntity;
import com.talentos.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

/**
 * Persistent domain entity representing a Hiring Manager Profile in TalentOS.
 *
 * <p>Extends {@link AuditableEntity} and holds profile data for users with the HIRING_MANAGER role.
 * Links 1:1 with {@link User} and N:1 with {@link Company}.
 *
 * <h3>Table: {@code hiring_managers}</h3>
 *
 * <h3>Soft Delete</h3>
 * <p>Records are logically deleted when {@code isDeleted = true}. The {@code @SQLRestriction}
 * annotation ensures all JPA queries automatically exclude deleted records.
 *
 * <h3>Relationships</h3>
 * <ul>
 *   <li>{@code user}    — 1:1 with User (owning side of FK {@code user_id})</li>
 *   <li>{@code company} — N:1 with Company (owning side of FK {@code company_id})</li>
 * </ul>
 */
@Entity
@Table(
        name = "hiring_managers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_hiring_managers_user_id", columnNames = "user_id")
        },
        indexes = {
                @Index(name = "idx_hiring_managers_user_id", columnList = "user_id"),
                @Index(name = "idx_hiring_managers_company_id", columnList = "company_id"),
                @Index(name = "idx_hiring_managers_is_deleted", columnList = "is_deleted")
        }
)
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class HiringManagerProfile extends AuditableEntity {

    /**
     * 1:1 relationship with the associated User identity.
     * Foreign key: {@code hiring_managers.user_id -> users.id}.
     * ON DELETE CASCADE per ERD §6.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * N:1 relationship with the employing Company.
     * Foreign key: {@code hiring_managers.company_id -> companies.id}.
     * ON DELETE RESTRICT per ERD §6.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * Job title or designation of the hiring manager within their company.
     * Examples: "Engineering Manager", "Director of Product".
     */
    @Size(max = 100, message = "Title must not exceed 100 characters")
    @Column(name = "title", length = 100)
    private String title;

    /**
     * Department or organizational unit led or managed by the hiring manager.
     * Examples: "Engineering", "Design", "Product Management".
     */
    @Size(max = 100, message = "Department must not exceed 100 characters")
    @Column(name = "department", length = 100)
    private String department;

    /**
     * Logical deletion flag.
     * {@code true}  = record is soft-deleted.
     * {@code false} = record is active (default).
     */
    @Column(name = "is_deleted", nullable = false)
    @lombok.Builder.Default
    private boolean isDeleted = false;

    /**
     * UUID of the user who performed the soft delete.
     * Set by the service layer alongside {@code deletedAt} (inherited from {@link AuditableEntity}).
     */
    @Column(name = "deleted_by", columnDefinition = "uuid")
    private UUID deletedBy;
}

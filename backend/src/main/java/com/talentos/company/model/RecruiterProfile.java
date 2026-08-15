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
 * Persistent domain entity representing a Recruiter Profile in TalentOS.
 *
 * <p>Extends {@link AuditableEntity} and holds profile data for users with the RECRUITER role.
 * Links 1:1 with {@link User} and N:1 with {@link Company}.
 *
 * <h3>Table: {@code recruiters}</h3>
 */
@Entity
@Table(
        name = "recruiters",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_recruiters_user_id", columnNames = "user_id")
        },
        indexes = {
                @Index(name = "idx_recruiters_user_id", columnList = "user_id"),
                @Index(name = "idx_recruiters_company_id", columnList = "company_id"),
                @Index(name = "idx_recruiters_is_deleted", columnList = "is_deleted")
        }
)
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RecruiterProfile extends AuditableEntity {

    /**
     * 1:1 relationship with the associated User identity.
     * Foreign key: {@code recruiters.user_id -> users.id}.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * N:1 relationship with the employing Company.
     * Foreign key: {@code recruiters.company_id -> companies.id}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * Job title or designation of the recruiter within their company.
     * Examples: "Senior Technical Recruiter", "Talent Acquisition Lead".
     */
    @Size(max = 100, message = "Title must not exceed 100 characters")
    @Column(name = "title", length = 100)
    private String title;

    /**
     * Contact phone number for the recruiter.
     */
    @Size(max = 50, message = "Phone number must not exceed 50 characters")
    @Column(name = "phone", length = 50)
    private String phone;

    /**
     * Logical deletion flag.
     * {@code true}  = record is soft-deleted.
     * {@code false} = record is active.
     */
    @Column(name = "is_deleted", nullable = false)
    @lombok.Builder.Default
    private boolean isDeleted = false;

    /**
     * UUID of the user who performed the soft delete.
     */
    @Column(name = "deleted_by", columnDefinition = "uuid")
    private UUID deletedBy;
}

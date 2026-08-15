package com.talentos.company.model;

import com.talentos.shared.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

/**
 * Persistent domain entity representing a Company in TalentOS.
 */
@Entity
@Table(
        name = "companies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_companies_slug", columnNames = "slug")
        },
        indexes = {
                @Index(name = "idx_companies_is_deleted", columnList = "is_deleted"),
                @Index(name = "idx_companies_name", columnList = "name")
        }
)
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Company extends AuditableEntity {

    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name must not exceed 255 characters")
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @NotBlank(message = "Company slug is required")
    @Size(max = 255, message = "Company slug must not exceed 255 characters")
    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 100, message = "Industry must not exceed 100 characters")
    @Column(name = "industry", length = 100)
    private String industry;

    @Size(max = 50, message = "Company size must not exceed 50 characters")
    @Column(name = "size", length = 50)
    private String size;

    @Size(max = 500, message = "Website URL must not exceed 500 characters")
    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Size(max = 500, message = "Logo URL must not exceed 500 characters")
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Size(max = 255, message = "Location must not exceed 255 characters")
    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "is_deleted", nullable = false)
    @lombok.Builder.Default
    private boolean isDeleted = false;

    @Column(name = "deleted_by", columnDefinition = "uuid")
    private UUID deletedBy;
}

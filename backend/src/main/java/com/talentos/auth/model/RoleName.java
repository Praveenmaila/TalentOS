package com.talentos.auth.model;

/**
 * Enumeration of all valid system roles in TalentOS.
 *
 * <p>These four values are the only roles that exist in the platform.
 * They map to the {@code name} column of the {@code roles} lookup table
 * (stored as VARCHAR with a CHECK constraint in PostgreSQL).
 *
 * <p>Roles are seeded once via Flyway and are never created at runtime
 * through the application layer.
 *
 * <p>Permissions are enforced via Spring Security method-level
 * {@code @PreAuthorize} annotations using these role names — there is
 * no separate {@code permissions} table in the TalentOS MVP ERD.
 *
 * <pre>
 * Role              │ Capabilities
 * ──────────────────┼─────────────────────────────────────────────────────
 * ADMIN             │ Full platform access; can manage users and config
 * RECRUITER         │ Manage jobs, companies, review applications, run AI
 * HIRING_MANAGER    │ View shortlisted candidates, add interview feedback
 * CANDIDATE         │ Own profile, resume upload, apply to jobs, own apps
 * </pre>
 *
 * <p>Spring Security authority format: {@code ROLE_<name>}
 * e.g., {@code ROLE_RECRUITER}, {@code ROLE_CANDIDATE}.
 */
public enum RoleName {

    /** Full platform administration access. Cannot be self-registered. */
    ADMIN,

    /** Creates and manages job postings and company profiles. */
    RECRUITER,

    /** Reviews shortlisted candidates and provides interview feedback. */
    HIRING_MANAGER,

    /** Manages own profile, uploads resumes, and applies to jobs. */
    CANDIDATE
}

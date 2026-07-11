# TalentOS — Entity Relationship Design (ERD)

> Version: 1.0
> Phase: System Design — Database
> Database: PostgreSQL (Neon)
> Migrations: Flyway
> Architecture: Modular Monolith (Spring Boot 3 / Spring Data JPA)

---

## 1. Entity Descriptions

### users
The central identity record for every person in the system. Every user has exactly one role. Role determines which profile table they extend. One user per email address.

### roles
A reference/lookup table representing the four system roles: `ADMIN`, `RECRUITER`, `HIRING_MANAGER`, `CANDIDATE`. Stored as a table (not just a column enum) to allow future role expansion and permission assignment without schema changes.

### companies
Organizations that post jobs. Created and managed by RECRUITERs or ADMINs. Jobs belong to a company. Recruiters are associated with a company.

### recruiters
Profile extension for users with the `RECRUITER` role. Linked one-to-one with `users`. Associated with a company they work for.

### hiring_managers
Profile extension for users with the `HIRING_MANAGER` role. Linked one-to-one with `users`. Associated with a company.

### candidates
Profile extension for users with the `CANDIDATE` role. Linked one-to-one with `users`. Contains professional profile data: headline, summary, location, LinkedIn, years of experience, and education details.

### jobs
Job postings created by recruiters under a company. Has a status lifecycle (`DRAFT` → `OPEN` → `CLOSED`). Contains job description, requirements, type, level, location, and salary range.

### applications
A candidate's formal application to a job. Has a pipeline status lifecycle (`APPLIED` → `SCREENING` → `INTERVIEW` → `OFFER` → `HIRED` / `REJECTED`). Connects candidates to jobs. Tracks the recruiter who reviewed and the hiring manager assigned.

### resumes
A candidate's uploaded resume file. Stores metadata only — the physical file lives in Supabase Storage. A candidate can have multiple resume versions; one is marked as primary.

### resume_analysis
The AI-generated analysis of a resume against a specific job. Persisted after each Gemini API call. Stores the full explainability output: match score, matching skills, missing skills, strengths, risks, AI summary, interview topics, and learning recommendations.

### skills
A normalized master list of all skills known to the platform. Skills are shared across candidates and jobs. Prevents duplicate skill names.

### candidate_skills
Junction table linking candidates to skills. Records the candidate's self-reported proficiency level and years of experience for each skill.

### job_skills
Junction table linking jobs to skills. Records whether each skill is required or preferred for the job.

### notifications
In-app notification records for any user. Triggered by application status changes and other platform events. Tracks read/unread state.

### activity_logs
An immutable audit trail of all significant user-initiated and system-initiated actions. Used for admin analytics and debugging. Never updated or soft-deleted.

---

## 2. Relationships

| From Entity       | Relationship | To Entity         | Via / Notes                                          |
|-------------------|-------------|-------------------|------------------------------------------------------|
| users             | 1:1         | roles             | `users.role_id → roles.id`                          |
| users             | 1:1         | recruiters        | `recruiters.user_id → users.id`                     |
| users             | 1:1         | hiring_managers   | `hiring_managers.user_id → users.id`                |
| users             | 1:1         | candidates        | `candidates.user_id → users.id`                     |
| recruiters        | N:1         | companies         | `recruiters.company_id → companies.id`              |
| hiring_managers   | N:1         | companies         | `hiring_managers.company_id → companies.id`         |
| jobs              | N:1         | companies         | `jobs.company_id → companies.id`                    |
| jobs              | N:1         | recruiters        | `jobs.created_by_recruiter_id → recruiters.id`      |
| applications      | N:1         | candidates        | `applications.candidate_id → candidates.id`         |
| applications      | N:1         | jobs              | `applications.job_id → jobs.id`                     |
| applications      | N:1         | recruiters        | `applications.reviewed_by_recruiter_id → recruiters.id` (nullable) |
| applications      | N:1         | hiring_managers   | `applications.assigned_hiring_manager_id → hiring_managers.id` (nullable) |
| resumes           | N:1         | candidates        | `resumes.candidate_id → candidates.id`              |
| resume_analysis   | N:1         | resumes           | `resume_analysis.resume_id → resumes.id`            |
| resume_analysis   | N:1         | jobs              | `resume_analysis.job_id → jobs.id` (nullable — profile-level analysis allowed) |
| candidate_skills  | N:1         | candidates        | `candidate_skills.candidate_id → candidates.id`     |
| candidate_skills  | N:1         | skills            | `candidate_skills.skill_id → skills.id`             |
| job_skills        | N:1         | jobs              | `job_skills.job_id → jobs.id`                       |
| job_skills        | N:1         | skills            | `job_skills.skill_id → skills.id`                   |
| notifications     | N:1         | users             | `notifications.recipient_user_id → users.id`        |
| notifications     | N:1         | applications      | `notifications.application_id → applications.id` (nullable) |
| activity_logs     | N:1         | users             | `activity_logs.actor_user_id → users.id` (nullable — system events allowed) |

---

## 3. Cardinality

```
roles           ||--o{ users              : "one role, many users"
users           ||--o| recruiters         : "one user is zero or one recruiter"
users           ||--o| hiring_managers    : "one user is zero or one hiring manager"
users           ||--o| candidates         : "one user is zero or one candidate"
companies       ||--o{ recruiters         : "one company, many recruiters"
companies       ||--o{ hiring_managers    : "one company, many hiring managers"
companies       ||--o{ jobs              : "one company, many jobs"
recruiters      ||--o{ jobs              : "one recruiter creates many jobs"
candidates      ||--o{ applications      : "one candidate, many applications"
jobs            ||--o{ applications      : "one job, many applications"
candidates      ||--o{ resumes           : "one candidate, many resume versions"
resumes         ||--o{ resume_analysis   : "one resume, many analyses (per job)"
jobs            ||--o{ resume_analysis   : "one job, many analyses against it"
candidates      }o--o{ skills            : "via candidate_skills (M:N)"
jobs            }o--o{ skills            : "via job_skills (M:N)"
users           ||--o{ notifications     : "one user, many notifications"
users           ||--o{ activity_logs     : "one user, many log entries"
```

---

## 4. Mermaid ER Diagram

```mermaid
erDiagram

    roles {
        uuid id PK
        varchar name UK
        varchar description
        timestamp created_at
        timestamp updated_at
    }

    users {
        uuid id PK
        uuid role_id FK
        varchar first_name
        varchar last_name
        varchar email UK
        varchar password_hash
        varchar avatar_url
        boolean is_active
        boolean is_deleted
        timestamp deleted_at
        uuid deleted_by
        timestamp created_at
        timestamp updated_at
        uuid created_by
        uuid updated_by
    }

    companies {
        uuid id PK
        varchar name
        varchar slug UK
        text description
        varchar industry
        varchar size
        varchar website_url
        varchar logo_url
        varchar location
        boolean is_deleted
        timestamp deleted_at
        uuid deleted_by
        timestamp created_at
        timestamp updated_at
        uuid created_by
        uuid updated_by
    }

    recruiters {
        uuid id PK
        uuid user_id FK UK
        uuid company_id FK
        varchar title
        varchar phone
        boolean is_deleted
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
        uuid created_by
        uuid updated_by
    }

    hiring_managers {
        uuid id PK
        uuid user_id FK UK
        uuid company_id FK
        varchar title
        varchar department
        boolean is_deleted
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
        uuid created_by
        uuid updated_by
    }

    candidates {
        uuid id PK
        uuid user_id FK UK
        varchar headline
        text summary
        varchar location
        varchar linkedin_url
        varchar github_url
        varchar portfolio_url
        int years_of_experience
        varchar highest_education
        varchar field_of_study
        boolean is_deleted
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
        uuid created_by
        uuid updated_by
    }

    jobs {
        uuid id PK
        uuid company_id FK
        uuid created_by_recruiter_id FK
        varchar title
        text description
        text requirements
        varchar status
        varchar type
        varchar level
        varchar location
        boolean is_remote
        int salary_min
        int salary_max
        varchar currency
        int openings
        timestamp closed_at
        boolean is_deleted
        timestamp deleted_at
        uuid deleted_by
        timestamp created_at
        timestamp updated_at
        uuid created_by
        uuid updated_by
    }

    applications {
        uuid id PK
        uuid candidate_id FK
        uuid job_id FK
        uuid reviewed_by_recruiter_id FK
        uuid assigned_hiring_manager_id FK
        varchar status
        text cover_letter
        text recruiter_notes
        text hiring_manager_feedback
        timestamp status_changed_at
        boolean is_deleted
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
        uuid created_by
        uuid updated_by
    }

    resumes {
        uuid id PK
        uuid candidate_id FK
        varchar file_name
        varchar file_url
        varchar file_key
        int file_size_bytes
        varchar mime_type
        boolean is_primary
        boolean is_deleted
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
        uuid created_by
        uuid updated_by
    }

    resume_analysis {
        uuid id PK
        uuid resume_id FK
        uuid job_id FK
        int match_score
        jsonb matching_skills
        jsonb missing_skills
        jsonb strengths
        jsonb risks
        text ai_summary
        jsonb interview_topics
        jsonb learning_recommendations
        varchar gemini_model_version
        timestamp analysed_at
        timestamp created_at
        timestamp updated_at
        uuid created_by
        uuid updated_by
    }

    skills {
        uuid id PK
        varchar name UK
        varchar category
        boolean is_deleted
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }

    candidate_skills {
        uuid id PK
        uuid candidate_id FK
        uuid skill_id FK
        varchar proficiency_level
        int years_of_experience
        timestamp created_at
        timestamp updated_at
    }

    job_skills {
        uuid id PK
        uuid job_id FK
        uuid skill_id FK
        boolean is_required
        timestamp created_at
        timestamp updated_at
    }

    notifications {
        uuid id PK
        uuid recipient_user_id FK
        uuid application_id FK
        varchar type
        varchar title
        text message
        boolean is_read
        timestamp read_at
        boolean is_deleted
        timestamp deleted_at
        timestamp created_at
    }

    activity_logs {
        uuid id PK
        uuid actor_user_id FK
        varchar action
        varchar entity_type
        uuid entity_id
        jsonb old_value
        jsonb new_value
        varchar ip_address
        varchar user_agent
        timestamp created_at
    }

    roles         ||--o{ users              : "has role"
    users         ||--o| recruiters         : "profile"
    users         ||--o| hiring_managers    : "profile"
    users         ||--o| candidates         : "profile"
    companies     ||--o{ recruiters         : "employs"
    companies     ||--o{ hiring_managers    : "employs"
    companies     ||--o{ jobs              : "posts"
    recruiters    ||--o{ jobs              : "creates"
    candidates    ||--o{ applications      : "submits"
    jobs          ||--o{ applications      : "receives"
    recruiters    ||--o{ applications      : "reviews"
    hiring_managers ||--o{ applications    : "evaluates"
    candidates    ||--o{ resumes           : "uploads"
    resumes       ||--o{ resume_analysis   : "analyzed as"
    jobs          ||--o{ resume_analysis   : "analyzed against"
    candidates    ||--o{ candidate_skills  : "has"
    skills        ||--o{ candidate_skills  : "tagged in"
    jobs          ||--o{ job_skills        : "requires"
    skills        ||--o{ job_skills        : "tagged in"
    users         ||--o{ notifications     : "receives"
    applications  ||--o{ notifications     : "triggers"
    users         ||--o{ activity_logs     : "performs"
```

---

## 5. Primary Keys

All primary keys are UUID v4, generated by the application layer (Java `UUID.randomUUID()`) before insert. This is consistent with Spring Data JPA's `@GeneratedValue(strategy = GenerationType.AUTO)` with a UUID generator.

| Table              | Primary Key | Type     | Notes                                      |
|--------------------|-------------|----------|--------------------------------------------|
| roles              | id          | UUID     | Seeded at startup via Flyway               |
| users              | id          | UUID     |                                            |
| companies          | id          | UUID     |                                            |
| recruiters         | id          | UUID     |                                            |
| hiring_managers    | id          | UUID     |                                            |
| candidates         | id          | UUID     |                                            |
| jobs               | id          | UUID     |                                            |
| applications       | id          | UUID     |                                            |
| resumes            | id          | UUID     |                                            |
| resume_analysis    | id          | UUID     |                                            |
| skills             | id          | UUID     |                                            |
| candidate_skills   | id          | UUID     | Surrogate PK; composite unique enforced separately |
| job_skills         | id          | UUID     | Surrogate PK; composite unique enforced separately |
| notifications      | id          | UUID     |                                            |
| activity_logs      | id          | UUID     |                                            |

---

## 6. Foreign Keys

| Table              | Column                          | References                  | On Delete Behavior |
|--------------------|---------------------------------|-----------------------------|--------------------|
| users              | role_id                         | roles(id)                   | RESTRICT           |
| recruiters         | user_id                         | users(id)                   | CASCADE            |
| recruiters         | company_id                      | companies(id)               | RESTRICT           |
| hiring_managers    | user_id                         | users(id)                   | CASCADE            |
| hiring_managers    | company_id                      | companies(id)               | RESTRICT           |
| candidates         | user_id                         | users(id)                   | CASCADE            |
| jobs               | company_id                      | companies(id)               | RESTRICT           |
| jobs               | created_by_recruiter_id         | recruiters(id)              | RESTRICT           |
| applications       | candidate_id                    | candidates(id)              | RESTRICT           |
| applications       | job_id                          | jobs(id)                    | RESTRICT           |
| applications       | reviewed_by_recruiter_id        | recruiters(id)              | SET NULL           |
| applications       | assigned_hiring_manager_id      | hiring_managers(id)         | SET NULL           |
| resumes            | candidate_id                    | candidates(id)              | CASCADE            |
| resume_analysis    | resume_id                       | resumes(id)                 | CASCADE            |
| resume_analysis    | job_id                          | jobs(id)                    | SET NULL           |
| candidate_skills   | candidate_id                    | candidates(id)              | CASCADE            |
| candidate_skills   | skill_id                        | skills(id)                  | RESTRICT           |
| job_skills         | job_id                          | jobs(id)                    | CASCADE            |
| job_skills         | skill_id                        | skills(id)                  | RESTRICT           |
| notifications      | recipient_user_id               | users(id)                   | CASCADE            |
| notifications      | application_id                  | applications(id)            | SET NULL           |
| activity_logs      | actor_user_id                   | users(id)                   | SET NULL           |

> **On Delete Rationale**
> - `CASCADE` — child records are meaningless without the parent (e.g., a candidate's resumes).
> - `RESTRICT` — prevents accidental deletion of referenced parent records (e.g., cannot delete a company that has jobs).
> - `SET NULL` — optional references; the record remains valid if the referenced party is removed (e.g., an application stays if a recruiter leaves).

---

## 7. Constraints

### Unique Constraints

| Table              | Column(s)                        | Purpose                                          |
|--------------------|----------------------------------|--------------------------------------------------|
| roles              | name                             | No duplicate role names                          |
| users              | email                            | One account per email address                    |
| companies          | slug                             | URL-safe unique company identifier               |
| recruiters         | user_id                          | One recruiter profile per user                   |
| hiring_managers    | user_id                          | One hiring manager profile per user              |
| candidates         | user_id                          | One candidate profile per user                   |
| skills             | name                             | No duplicate skill names (case-insensitive via index) |
| candidate_skills   | (candidate_id, skill_id)         | A candidate cannot tag the same skill twice      |
| job_skills         | (job_id, skill_id)               | A job cannot require the same skill twice        |
| applications       | (candidate_id, job_id)           | A candidate can apply to a job only once         |

### Check Constraints

| Table              | Column(s)                              | Constraint                                             |
|--------------------|----------------------------------------|--------------------------------------------------------|
| jobs               | status                                 | IN ('DRAFT', 'OPEN', 'CLOSED')                        |
| jobs               | type                                   | IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP') |
| jobs               | level                                  | IN ('JUNIOR', 'MID', 'SENIOR', 'LEAD', 'EXECUTIVE')  |
| jobs               | salary_min, salary_max                 | salary_min <= salary_max                              |
| jobs               | openings                               | openings >= 1                                         |
| applications       | status                                 | IN ('APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED') |
| candidate_skills   | proficiency_level                      | IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT') |
| candidate_skills   | years_of_experience                    | years_of_experience >= 0                              |
| candidates         | years_of_experience                    | years_of_experience >= 0                              |
| resume_analysis    | match_score                            | match_score BETWEEN 0 AND 100                         |
| resumes            | file_size_bytes                        | file_size_bytes > 0                                   |
| notifications      | type                                   | IN ('APPLICATION_RECEIVED', 'STATUS_CHANGED', 'SHORTLISTED', 'REJECTED', 'OFFER_MADE', 'HIRED', 'SYSTEM') |

### Not Null Constraints (Critical Columns)

| Table              | NOT NULL Columns                                                   |
|--------------------|--------------------------------------------------------------------|
| users              | role_id, first_name, last_name, email, password_hash, is_active    |
| companies          | name, slug                                                         |
| recruiters         | user_id, company_id                                                |
| hiring_managers    | user_id, company_id                                                |
| candidates         | user_id                                                            |
| jobs               | company_id, created_by_recruiter_id, title, status, type, level   |
| applications       | candidate_id, job_id, status                                       |
| resumes            | candidate_id, file_name, file_url, file_key, is_primary            |
| resume_analysis    | resume_id, match_score, ai_summary, analysed_at                   |
| skills             | name                                                               |
| candidate_skills   | candidate_id, skill_id, proficiency_level                          |
| job_skills         | job_id, skill_id, is_required                                      |
| notifications      | recipient_user_id, type, title, message, is_read                  |
| activity_logs      | action, entity_type, created_at                                    |

---

## 8. Index Strategy

Indexes are designed around the three primary access patterns: **filtered list views**, **foreign key joins**, and **full-text search**.

### Primary & Unique (Auto-created)

| Table              | Index                                               |
|--------------------|-----------------------------------------------------|
| All tables         | Primary key index on `id`                           |
| users              | Unique index on `email`                             |
| companies          | Unique index on `slug`                              |
| roles              | Unique index on `name`                              |
| skills             | Unique index on `lower(name)` (functional index, case-insensitive) |
| recruiters         | Unique index on `user_id`                           |
| hiring_managers    | Unique index on `user_id`                           |
| candidates         | Unique index on `user_id`                           |
| candidate_skills   | Unique composite on `(candidate_id, skill_id)`      |
| job_skills         | Unique composite on `(job_id, skill_id)`            |
| applications       | Unique composite on `(candidate_id, job_id)`        |

### Performance Indexes (Explicit)

| Table              | Index Columns                           | Query Pattern Served                                 |
|--------------------|-----------------------------------------|------------------------------------------------------|
| users              | (role_id)                               | Filter users by role (admin user management)         |
| users              | (is_deleted, is_active)                 | Exclude soft-deleted / inactive users globally       |
| jobs               | (company_id, status)                    | Job list filtered by company + status                |
| jobs               | (status, is_deleted)                    | Public job board queries (OPEN, not deleted)         |
| jobs               | (created_by_recruiter_id)               | Recruiter's own job listing                          |
| jobs               | (created_at DESC)                       | Default job list sort (newest first)                 |
| applications       | (job_id, status)                        | Pipeline board — all apps for a job grouped by status |
| applications       | (candidate_id, status)                  | Candidate's own application tracking                 |
| applications       | (reviewed_by_recruiter_id)              | Recruiter workload view                              |
| applications       | (status_changed_at DESC)                | Recent activity ordering                             |
| resumes            | (candidate_id, is_primary)              | Fetch candidate's primary resume quickly             |
| resume_analysis    | (resume_id, job_id)                     | Retrieve cached analysis for a resume+job pair       |
| resume_analysis    | (match_score DESC)                      | Candidate ranking sorted by AI score                 |
| candidate_skills   | (skill_id)                              | Find all candidates with a given skill               |
| job_skills         | (skill_id)                              | Find all jobs requiring a given skill                |
| notifications      | (recipient_user_id, is_read, is_deleted)| Unread notification count + list                     |
| notifications      | (created_at DESC)                       | Notification feed ordering                           |
| activity_logs      | (actor_user_id, created_at DESC)        | User activity history                                |
| activity_logs      | (entity_type, entity_id)               | Audit trail for a specific record                    |
| companies          | (is_deleted)                            | Filter active companies                              |

### Full-Text Search Index

| Table     | Index                                          | Purpose                              |
|-----------|------------------------------------------------|--------------------------------------|
| jobs      | GIN index on `to_tsvector('english', title \|\| ' ' \|\| description)` | Job title + description search       |
| candidates | GIN index on `to_tsvector('english', headline \|\| ' ' \|\| coalesce(summary, ''))` | Candidate profile search             |
| companies | GIN index on `to_tsvector('english', name \|\| ' ' \|\| coalesce(description, ''))` | Company name search                  |
| skills    | GIN index on `to_tsvector('english', name)`   | Skill autocomplete / search          |

---

## 9. Audit Strategy

Every mutable entity (excluding `activity_logs` which is immutable by design) carries four audit columns managed automatically by Spring Data JPA with `@EnableJpaAuditing`.

### Audit Columns (applied to all main entities)

| Column       | Type        | Populated By                         | Purpose                                      |
|--------------|-------------|--------------------------------------|----------------------------------------------|
| created_at   | TIMESTAMPTZ | Spring `@CreatedDate` — set on insert | When the record was first created            |
| updated_at   | TIMESTAMPTZ | Spring `@LastModifiedDate` — set on update | When the record was last modified        |
| created_by   | UUID        | Spring `@CreatedBy` — from SecurityContext | Which user created the record            |
| updated_by   | UUID        | Spring `@LastModifiedBy` — from SecurityContext | Which user last modified the record |

### Implementation Notes

- All entities extend a `BaseEntity` mapped superclass (`@MappedSuperclass`) that carries these four fields.
- `AuditorAware<UUID>` implementation reads the current user's UUID from the Spring Security `SecurityContextHolder`.
- Timestamps are stored as `TIMESTAMP WITH TIME ZONE` (UTC). Display conversion happens in the frontend.
- `activity_logs` is the deeper audit trail — it stores `old_value` and `new_value` as JSONB snapshots for sensitive changes (status transitions, user role changes, etc.).

### activity_logs Coverage

The following actions are logged in `activity_logs`:

| Action                    | Entity Type     | Notes                                    |
|---------------------------|-----------------|------------------------------------------|
| USER_REGISTERED           | users           | Logged at signup                         |
| USER_ROLE_CHANGED         | users           | Admin action                             |
| JOB_CREATED               | jobs            |                                          |
| JOB_STATUS_CHANGED        | jobs            | DRAFT→OPEN, OPEN→CLOSED                  |
| APPLICATION_SUBMITTED     | applications    | Candidate applies                        |
| APPLICATION_STATUS_CHANGED| applications    | Pipeline stage transition                |
| RESUME_UPLOADED           | resumes         |                                          |
| RESUME_ANALYSIS_TRIGGERED | resume_analysis | Records Gemini model version used        |
| COMPANY_CREATED           | companies       |                                          |

---

## 10. Soft Delete Strategy

Soft delete prevents permanent data loss while allowing logical deletion from the UI. Deleted records are excluded from all standard queries but remain available for audit and analytics.

### Soft Delete Columns

| Column       | Type        | Default | Description                               |
|--------------|-------------|---------|-------------------------------------------|
| is_deleted   | BOOLEAN     | false   | Flag indicating logical deletion          |
| deleted_at   | TIMESTAMPTZ | NULL    | Timestamp of deletion                     |
| deleted_by   | UUID        | NULL    | UUID of the user who performed the delete |

### Tables with Soft Delete

| Table              | Soft Delete? | Reasoning                                               |
|--------------------|--------------|---------------------------------------------------------|
| users              | YES          | Preserve account history, applications, activity logs   |
| companies          | YES          | Jobs and applications reference companies               |
| recruiters         | YES          | Applications reference recruiters                       |
| hiring_managers    | YES          | Applications reference hiring managers                  |
| candidates         | YES          | Applications and resumes reference candidates           |
| jobs               | YES          | Applications reference jobs; analytics require job data |
| applications       | YES          | Core business record; must not be permanently deleted   |
| resumes            | YES          | Preserve file metadata even if file is removed          |
| skills             | YES          | Skills referenced by candidate_skills and job_skills    |
| notifications      | YES          | Allow users to "dismiss" notifications                  |
| resume_analysis    | NO           | Derived/computed data; can be re-generated; CASCADE delete with resume |
| candidate_skills   | NO           | Junction table; rows are replaced, not soft-deleted      |
| job_skills         | NO           | Junction table; rows are replaced, not soft-deleted      |
| roles              | NO           | System-seeded reference data; never deleted             |
| activity_logs      | NO           | Immutable audit trail; must never be deleted            |

### Query Pattern

All Spring Data JPA repositories for soft-deletable entities apply a global filter:

```
WHERE is_deleted = false
```

This is implemented using Spring Data JPA `@Where(clause = "is_deleted = false")` at the entity level, ensuring all derived queries and specification-based queries automatically exclude deleted records without manual filtering in service layer code.

Admin endpoints (if needed) bypass this filter using native queries or a separate repository method annotated with `@Query` that includes deleted records.

---

## Summary

| Concern               | Decision                                                           |
|-----------------------|--------------------------------------------------------------------|
| Primary Keys          | UUID v4 — application-generated                                    |
| Normalization         | 3NF — skills, roles, and profiles are normalized into separate tables |
| Soft Delete           | `is_deleted` + `deleted_at` + `deleted_by` on all core entities   |
| Audit Fields          | `created_at`, `updated_at`, `created_by`, `updated_by` via Spring JPA Auditing |
| Activity Logging      | Immutable `activity_logs` table with JSONB old/new value snapshots |
| Foreign Keys          | All relationships enforced at DB level with appropriate ON DELETE behavior |
| Indexing              | Composite indexes per query pattern + GIN full-text indexes on search targets |
| AI Analysis Storage   | Persisted JSONB in `resume_analysis` — cached per resume+job pair |
| Many-to-Many          | Explicit junction tables: `candidate_skills`, `job_skills`         |
| Timestamp Precision   | `TIMESTAMP WITH TIME ZONE` (UTC) for all time columns             |
| Enum Storage          | Stored as `VARCHAR` in PostgreSQL with CHECK constraints; mapped to Java enums via JPA |

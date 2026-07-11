# TalentOS — Validation Rules

> Version: 1.0
> Phase: System Design — Validation
> Applies to: Backend (Spring Boot Bean Validation) and Frontend (Zod schemas)
> Source of Truth: docs/TalentOS.md · docs/ERD.md · docs/api-contract.md

---

## Conventions

### Validation Layers

| Layer     | Tool                          | When Applied              |
|-----------|-------------------------------|---------------------------|
| Frontend  | Zod + React Hook Form         | Before form submission     |
| Backend   | Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Size`, etc.) | On every controller input |
| Database  | PostgreSQL CHECK + NOT NULL + UNIQUE | Final enforcement layer |

All three layers must be consistent. The backend is the authoritative layer; the frontend validates for UX only.

### Error Format

Validation failures return HTTP 400 with the standard error envelope:

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "fieldName": "Descriptive error message",
    "anotherField": "Another message"
  },
  "timestamp": "2026-07-11T09:00:00Z"
}
```

---

## 1. Users / Authentication

### Signup Request

#### Required Fields

| Field       | Type   | Required |
|-------------|--------|----------|
| firstName   | String | Yes      |
| lastName    | String | Yes      |
| email       | String | Yes      |
| password    | String | Yes      |
| role        | Enum   | Yes      |

#### Optional Fields

| Field    | Type   | Notes                  |
|----------|--------|------------------------|
| avatarUrl | String | Can be set post-signup |

#### Length Rules

| Field     | Min | Max | Note                    |
|-----------|-----|-----|-------------------------|
| firstName | 1   | 50  | After trimming whitespace |
| lastName  | 1   | 50  | After trimming whitespace |
| email     | 5   | 254 | RFC 5321 maximum        |
| password  | 8   | 128 | Stored as BCrypt hash   |

#### Regex

| Field     | Pattern                                                   | Purpose                       |
|-----------|-----------------------------------------------------------|-------------------------------|
| email     | `^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$`  | Valid email format            |
| password  | `^(?=.*[A-Z])(?=.*[0-9]).{8,}$`                          | Min 1 uppercase + 1 digit     |
| firstName | `^[a-zA-Z\s'\-]{1,50}$`                                  | Letters, spaces, hyphens, apostrophes |
| lastName  | `^[a-zA-Z\s'\-]{1,50}$`                                  | Same as firstName             |
| avatarUrl | `^https?://.+`                                            | Must be a valid absolute URL  |

#### Enum Validation

| Field | Allowed Values                                          | Notes                             |
|-------|---------------------------------------------------------|-----------------------------------|
| role  | `RECRUITER`, `HIRING_MANAGER`, `CANDIDATE`              | `ADMIN` cannot self-register      |

#### Duplicate Checks

| Field | Scope     | Error Message                               |
|-------|-----------|---------------------------------------------|
| email | Global    | "An account with this email already exists" |

#### Business Rules

- `ADMIN` role cannot be selected during self-registration. Admin accounts are created by seeding or by another Admin.
- Email must be lowercased and trimmed before storage and before duplicate check.
- Password must not be the same as the email address.
- Deactivated user accounts (`is_active = false`) cannot log in; login returns 401.

---

### Login Request

#### Required Fields

| Field    | Type   |
|----------|--------|
| email    | String |
| password | String |

#### Business Rules

- Email is case-insensitive during lookup.
- Return a generic error for both "user not found" and "wrong password" to prevent user enumeration: `"Invalid email or password"`.
- Deactivated accounts return the same generic 401.

---

### Update User (PUT /users/me)

#### Required Fields

| Field     | Type   |
|-----------|--------|
| firstName | String |
| lastName  | String |

#### Optional Fields

| Field     | Type   |
|-----------|--------|
| avatarUrl | String |

#### Length and Regex: same as Signup.

---

### Change Password (PATCH /users/me/password)

#### Required Fields

| Field           | Type   |
|-----------------|--------|
| currentPassword | String |
| newPassword     | String |
| confirmPassword | String |

#### Cross-Field Validation

| Rule                                    | Error Message                              |
|-----------------------------------------|--------------------------------------------|
| `newPassword` == `confirmPassword`      | "Passwords do not match"                   |
| `newPassword` != `currentPassword`      | "New password must differ from current"    |
| `currentPassword` matches stored BCrypt | "Current password is incorrect" (401/422)  |

#### Regex and Length: Same as `password` in Signup.

---

## 2. Companies

### Create / Update Company

#### Required Fields

| Field    | Type   |
|----------|--------|
| name     | String |
| industry | String |

#### Optional Fields

| Field       | Type   | Notes                    |
|-------------|--------|--------------------------|
| description | String |                          |
| size        | Enum   |                          |
| websiteUrl  | String | Must be a valid URL      |
| logoUrl     | String | Must be a valid URL      |
| location    | String |                          |

#### Length Rules

| Field       | Min | Max  |
|-------------|-----|------|
| name        | 2   | 100  |
| industry    | 2   | 100  |
| description | 0   | 2000 |
| location    | 0   | 150  |

#### Regex

| Field      | Pattern              | Purpose               |
|------------|----------------------|-----------------------|
| websiteUrl | `^https?://.+`       | Absolute URL required |
| logoUrl    | `^https?://.+`       | Absolute URL required |

#### Enum Validation

| Field | Allowed Values                                   |
|-------|--------------------------------------------------|
| size  | `1-10`, `11-50`, `51-200`, `201-500`, `501-1000`, `1000+` |

#### Duplicate Checks

| Field | Scope  | Notes                                               |
|-------|--------|-----------------------------------------------------|
| name  | Global | Case-insensitive check. Slug is derived from name.  |

#### Business Rules

- `slug` is auto-generated from `name` (e.g., "Acme Corp" → `"acme-corp"`) by the backend. It is never accepted as user input.
- A company cannot be deleted if it has `OPEN` jobs. Must close all jobs first (returns 422).
- `logoUrl` and `websiteUrl` must use `https://` in production environments.

---

## 3. Jobs

### Create / Update Job

#### Required Fields

| Field                   | Type    |
|-------------------------|---------|
| companyId               | UUID    |
| title                   | String  |
| description             | String  |
| type                    | Enum    |
| level                   | Enum    |
| openings                | Integer |

#### Optional Fields

| Field        | Type    | Notes                                         |
|--------------|---------|-----------------------------------------------|
| requirements | String  | Freetext requirements section                 |
| location     | String  |                                               |
| isRemote     | Boolean | Default: false                                |
| salaryMin    | Integer | In smallest currency unit (e.g., INR paise or whole INR) |
| salaryMax    | Integer |                                               |
| currency     | String  | Default: `"INR"`                              |
| skillIds     | UUID[]  | Optional list of skills to tag the job with   |

#### Length Rules

| Field        | Min | Max   |
|--------------|-----|-------|
| title        | 5   | 150   |
| description  | 50  | 10000 |
| requirements | 0   | 5000  |
| location     | 0   | 150   |
| currency     | 3   | 3     |

#### Enum Validation

| Field  | Allowed Values                                              |
|--------|-------------------------------------------------------------|
| type   | `FULL_TIME`, `PART_TIME`, `CONTRACT`, `INTERNSHIP`          |
| level  | `JUNIOR`, `MID`, `SENIOR`, `LEAD`, `EXECUTIVE`             |
| status | `DRAFT`, `OPEN`, `CLOSED` (set via PATCH /status, not on create) |

#### Cross-Field Validation

| Rule                              | Error Message                                      |
|-----------------------------------|----------------------------------------------------|
| `salaryMin <= salaryMax`          | "Minimum salary cannot exceed maximum salary"      |
| If `salaryMax` is set, `salaryMin` must also be set | "Minimum salary is required when maximum is specified" |
| `openings >= 1`                   | "At least one opening is required"                 |

#### Business Rules

- `companyId` must belong to a company the requesting Recruiter is associated with (or Admin can use any).
- `status` always starts as `DRAFT` on creation regardless of what the caller sends.
- Allowed status transitions: `DRAFT → OPEN`, `OPEN → CLOSED`. Reverse transitions are rejected (422).
- A `CLOSED` job cannot be re-opened.
- A job with active applications (`APPLIED`, `SCREENING`, `INTERVIEW`, `OFFER`) cannot be soft-deleted (422).
- Each `skillId` in `skillIds` must reference a valid, non-deleted skill UUID.
- `currency` must be a valid ISO 4217 3-letter code (e.g., `INR`, `USD`, `EUR`).

#### Regex

| Field    | Pattern                              |
|----------|--------------------------------------|
| currency | `^[A-Z]{3}$`                         |

---

### Job Status Transition (PATCH /jobs/{id}/status)

#### Required Fields

| Field  | Type |
|--------|------|
| status | Enum |

#### Business Rules (Status Machine)

| Current Status | Allowed Next Status | Rejected With |
|----------------|---------------------|---------------|
| `DRAFT`        | `OPEN`              | —             |
| `OPEN`         | `CLOSED`            | —             |
| `CLOSED`       | (none)              | 422           |
| Any backward   | (not allowed)       | 422           |

---

## 4. Candidates

### Update Candidate Profile (PUT /candidates/{id})

#### Required Fields

_None — all fields are optional updates._

#### Optional Fields

| Field             | Type    | Notes                              |
|-------------------|---------|------------------------------------|
| headline          | String  |                                    |
| summary           | String  |                                    |
| location          | String  |                                    |
| linkedinUrl       | String  | Must be a valid LinkedIn URL       |
| githubUrl         | String  | Must be a valid GitHub URL         |
| portfolioUrl      | String  | Must be a valid URL                |
| yearsOfExperience | Integer |                                    |
| highestEducation  | String  |                                    |
| fieldOfStudy      | String  |                                    |

#### Length Rules

| Field             | Min | Max  |
|-------------------|-----|------|
| headline          | 0   | 150  |
| summary           | 0   | 2000 |
| location          | 0   | 150  |
| highestEducation  | 0   | 150  |
| fieldOfStudy      | 0   | 150  |

#### Regex

| Field        | Pattern                                        | Notes                    |
|--------------|------------------------------------------------|--------------------------|
| linkedinUrl  | `^https://(www\.)?linkedin\.com/in/.+`         | Must be a linkedin.com URL |
| githubUrl    | `^https://(www\.)?github\.com/.+`              | Must be a github.com URL |
| portfolioUrl | `^https?://.+`                                 | Any valid URL            |

#### Cross-Field Validation

| Rule                        | Error Message                              |
|-----------------------------|--------------------------------------------|
| `yearsOfExperience >= 0`    | "Years of experience cannot be negative"   |

#### Business Rules

- A Candidate can only update their own profile. Attempting to update another candidate's profile returns 403.
- `yearsOfExperience` — maximum value: 50 (sanity cap).

---

### Candidate Skills (POST /candidates/{id}/skills)

#### Required Fields (per skill object)

| Field             | Type    |
|-------------------|---------|
| skillId           | UUID    |
| proficiencyLevel  | Enum    |

#### Optional Fields

| Field             | Type    | Default |
|-------------------|---------|---------|
| yearsOfExperience | Integer | 0       |

#### Enum Validation

| Field            | Allowed Values                                  |
|------------------|-------------------------------------------------|
| proficiencyLevel | `BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `EXPERT` |

#### Cross-Field Validation

| Rule                        | Error Message                                  |
|-----------------------------|------------------------------------------------|
| `yearsOfExperience >= 0`    | "Years of experience cannot be negative"       |
| `skillId` must exist        | "Skill not found: {skillId}"                   |

#### Business Rules

- This endpoint **replaces** all existing candidate skills. An empty array clears all skills.
- Maximum 50 skills per candidate.
- Duplicate `skillId` values in a single request are rejected (400).

---

## 5. Applications

### Submit Application (POST /applications)

#### Required Fields

| Field    | Type |
|----------|------|
| jobId    | UUID |
| resumeId | UUID |

#### Optional Fields

| Field       | Type   | Notes               |
|-------------|--------|---------------------|
| coverLetter | String | Max 3000 characters |

#### Length Rules

| Field       | Min | Max  |
|-------------|-----|------|
| coverLetter | 0   | 3000 |

#### Duplicate Checks

| Scope                        | Error Message                                    |
|------------------------------|--------------------------------------------------|
| (candidate_id, job_id) combo | "You have already applied to this job" (409)     |

#### Business Rules

- The `jobId` must reference a job with status `OPEN`. Applying to `DRAFT` or `CLOSED` jobs returns 422.
- The `resumeId` must belong to the authenticated candidate. A recruiter cannot submit an application on behalf of a candidate.
- Application status is always set to `APPLIED` on creation regardless of request payload.
- A candidate cannot have more than one active application per job.

---

### Update Application Status (PATCH /applications/{id}/status)

#### Required Fields

| Field          | Type   |
|----------------|--------|
| status         | Enum   |

#### Optional Fields

| Field          | Type   | Notes              |
|----------------|--------|--------------------|
| recruiterNotes | String | Max 3000 characters |

#### Enum Validation

| Field  | Allowed Values                                                    |
|--------|-------------------------------------------------------------------|
| status | `APPLIED`, `SCREENING`, `INTERVIEW`, `OFFER`, `HIRED`, `REJECTED` |

#### Business Rules (Status Machine)

| Current Status | Allowed Next Status              |
|----------------|----------------------------------|
| `APPLIED`      | `SCREENING`, `REJECTED`          |
| `SCREENING`    | `INTERVIEW`, `REJECTED`          |
| `INTERVIEW`    | `OFFER`, `REJECTED`              |
| `OFFER`        | `HIRED`, `REJECTED`              |
| `HIRED`        | (none — terminal state)          |
| `REJECTED`     | (none — terminal state)          |

- Moving to a previous status (e.g., `INTERVIEW → APPLIED`) is rejected (422).
- `HIRED` and `REJECTED` are terminal states and cannot be changed.
- Only the recruiter who manages the job (or Admin) can change application status.

---

### Hiring Manager Feedback (PATCH /applications/{id}/feedback)

#### Required Fields

| Field                  | Type   |
|------------------------|--------|
| hiringManagerFeedback  | String |

#### Length Rules

| Field                  | Min | Max  |
|------------------------|-----|------|
| hiringManagerFeedback  | 1   | 3000 |

#### Business Rules

- Feedback can only be submitted when application status is `INTERVIEW`, `OFFER`, `HIRED`, or `REJECTED`.
- Submitting feedback to an `APPLIED` or `SCREENING` application returns 422.
- Only the hiring manager assigned to the application (or Admin) can submit feedback.

---

## 6. Resumes

### Resume Upload (POST /resumes/upload)

#### Required Fields

| Field       | Type    |
|-------------|---------|
| file        | File    |
| candidateId | UUID    |

#### Optional Fields

| Field     | Type    | Default | Notes                                          |
|-----------|---------|---------|------------------------------------------------|
| isPrimary | Boolean | false   | Set true to mark this as the active resume     |
| jobId     | UUID    | —       | If provided, triggers immediate AI analysis    |

#### File Validation

| Rule                  | Value                    | Error Message                          |
|-----------------------|--------------------------|----------------------------------------|
| MIME type             | `application/pdf` only   | "Only PDF files are accepted"          |
| Maximum file size     | 5 MB (5,242,880 bytes)   | "File size must not exceed 5 MB"       |
| Minimum file size     | 1 KB (1,024 bytes)       | "Uploaded file appears to be empty"    |
| File extension        | `.pdf`                   | "File must have a .pdf extension"      |

#### Business Rules

- A `CANDIDATE` can only upload resumes for their own `candidateId`. Uploading for another candidate returns 403.
- A `RECRUITER` or `ADMIN` can upload on behalf of any candidate.
- When `isPrimary = true`, the service automatically sets all other resumes for this candidate to `isPrimary = false`.
- If `jobId` is provided, it must reference a valid (non-deleted) job.
- Maximum **10 resumes** stored per candidate. Uploading beyond this limit returns 422: "Resume limit reached. Delete an existing resume to upload a new one."

---

## 7. Resume Analysis

### Trigger Analysis (POST /resumes/{id}/analyse)

#### Required Fields

| Field | Type |
|-------|------|
| jobId | UUID |

#### Optional Fields

| Field | Type    | Default | Notes                                            |
|-------|---------|---------|--------------------------------------------------|
| force | Boolean | false   | If true, bypasses cache and re-calls Gemini API  |

#### Business Rules

- `jobId` must reference an existing, non-deleted job (any status — CLOSED jobs can still be analyzed for reference).
- The resume must belong to a candidate the requester has access to.
- If an analysis for the exact `(resume_id, job_id)` pair already exists and `force = false`, the cached result is returned without calling Gemini.
- If `force = true`, a new analysis is created and the old one is overwritten (or a new record is inserted — implementation choice).
- If the Gemini API call fails, a 500 is returned and no analysis record is saved.
- `match_score` returned by Gemini must be between 0 and 100. If out of range, it is clamped before storage.

---

## 8. Skills

### Create Skill (POST /skills)

#### Required Fields

| Field | Type   |
|-------|--------|
| name  | String |

#### Optional Fields

| Field    | Type   | Notes          |
|----------|--------|----------------|
| category | String | Max 50 chars   |

#### Length Rules

| Field    | Min | Max |
|----------|-----|-----|
| name     | 1   | 100 |
| category | 0   | 50  |

#### Duplicate Checks

| Field | Scope  | Notes                                                   |
|-------|--------|---------------------------------------------------------|
| name  | Global | Case-insensitive. "java" and "Java" are the same skill. |

#### Business Rules

- Skill names are stored with their original casing (e.g., `"Spring Boot"`) but uniqueness is enforced case-insensitively via a functional index on `lower(name)`.
- A skill that is referenced by at least one `candidate_skills` or `job_skills` record cannot be soft-deleted (422).

---

## 9. Notifications

### Notification Creation (Internal — System Generated)

Notifications are created internally by service layer events, not via direct API input. The following rules apply to system-generated records.

#### Required Fields (Internal)

| Field              | Type   |
|--------------------|--------|
| recipientUserId    | UUID   |
| type               | Enum   |
| title              | String |
| message            | String |

#### Enum Validation

| Field | Allowed Values                                                                                   |
|-------|--------------------------------------------------------------------------------------------------|
| type  | `APPLICATION_RECEIVED`, `STATUS_CHANGED`, `SHORTLISTED`, `REJECTED`, `OFFER_MADE`, `HIRED`, `SYSTEM` |

#### Length Rules

| Field   | Min | Max  |
|---------|-----|------|
| title   | 1   | 100  |
| message | 1   | 500  |

#### Business Rules

- Notifications are generated automatically when:
  - A candidate submits an application → recruiter receives `APPLICATION_RECEIVED`
  - An application status changes → candidate receives `STATUS_CHANGED`
  - Status moves to `OFFER` → candidate receives `OFFER_MADE`
  - Status moves to `HIRED` → candidate receives `HIRED`
  - Status moves to `REJECTED` → candidate receives `REJECTED`
- A user can only read or dismiss their own notifications.
- `read_at` is set when `is_read` transitions from `false` to `true` and cannot be unset.

---

## 10. Activity Logs

Activity logs are **write-only** from the application layer. No update or delete operations exist. The following fields are validated before insertion.

#### Required Fields (Internal)

| Field       | Type   |
|-------------|--------|
| action      | String |
| entityType  | String |
| createdAt   | Timestamp |

#### Optional Fields

| Field        | Type   | Notes                                        |
|--------------|--------|----------------------------------------------|
| actorUserId  | UUID   | Nullable for system-triggered events         |
| entityId     | UUID   | The UUID of the affected record              |
| oldValue     | JSONB  | Snapshot of state before change              |
| newValue     | JSONB  | Snapshot of state after change               |
| ipAddress    | String | From request context                         |
| userAgent    | String | From request context                         |

#### Business Rules

- Activity logs are **never deleted** — no soft-delete, no hard delete.
- `oldValue` and `newValue` must not contain raw passwords or JWT tokens.
- `ipAddress` must be sanitized/anonymized if storing for GDPR compliance.

---

## 11. Cross-Entity Business Rules

These rules span multiple entities and must be enforced at the service layer.

### User → Profile Consistency

| Rule | Detail |
|------|--------|
| Every `RECRUITER` user must have exactly one `recruiters` record | Created at signup |
| Every `HIRING_MANAGER` user must have exactly one `hiring_managers` record | Created at signup |
| Every `CANDIDATE` user must have exactly one `candidates` record | Created at signup |
| `ADMIN` users have no profile extension table | Admin profile data lives in `users` only |
| A user cannot belong to more than one role simultaneously | Enforced by `users.role_id` single FK |

---

### Recruiter → Company Association

| Rule | Detail |
|------|--------|
| A recruiter must be associated with a company to post jobs | `recruiters.company_id` must not be null |
| A recruiter can only create jobs under their associated company | Enforced in `JobService` by comparing `recruiters.company_id` with `jobs.company_id` |
| A recruiter can only manage applications for jobs belonging to their company | Enforced in `ApplicationService` |

---

### Candidate → Application Limits

| Rule | Detail |
|------|--------|
| One application per candidate per job | Unique constraint on `(candidate_id, job_id)` in `applications` table |
| Cannot apply to a non-`OPEN` job | Status check in `ApplicationService` before insert |
| Resume used in an active application cannot be deleted | Checked before soft-delete in `ResumeService` |

---

### Resume → Analysis Consistency

| Rule | Detail |
|------|--------|
| Analysis is scoped to a (resume, job) pair | Unique analysis per pair when `force = false` |
| A deleted resume's analyses are cascade-deleted | FK: `resume_analysis.resume_id → resumes.id ON DELETE CASCADE` |
| `match_score` must be 0–100 | Validated after Gemini response parsing; clamped if out of range |

---

### Job → Application Lifecycle

| Rule | Detail |
|------|--------|
| Closing a job does not reject pending applications | Existing applications retain their current status |
| Deleting a job is blocked if active applications exist | `APPLIED`, `SCREENING`, `INTERVIEW`, `OFFER` statuses block delete |
| Hired or rejected applications do not block job deletion | Terminal states are excluded from the active check |

---

## 12. Global Validation Rules

These rules apply to all entities.

### UUIDs

- All `id` fields sent by the client must be valid UUID v4 format.
- Pattern: `^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`
- Invalid UUID format returns 400: `"Invalid identifier format"`

### Pagination Parameters

| Parameter | Min | Max | Default | Error if Exceeded            |
|-----------|-----|-----|---------|------------------------------|
| page      | 0   | —   | 0       | "Page must be 0 or greater"  |
| size      | 1   | 50  | 10      | "Page size must be between 1 and 50" |

### Timestamps

- All timestamps are sent and received in **ISO 8601 UTC format**: `2026-07-11T08:30:00Z`
- Clients must not send `created_at`, `updated_at`, `deleted_at` — these are server-controlled.

### String Sanitization

- All string inputs are trimmed of leading and trailing whitespace before validation and storage.
- HTML tags are stripped from all freetext inputs (`description`, `summary`, `coverLetter`, `recruiterNotes`, `feedback`).
- SQL injection prevention is handled by parameterized JPA queries — not by input sanitization alone.

### Soft-Deleted Records

- Any request referencing a soft-deleted resource by ID (e.g., a deleted job UUID in an application request) returns 404: `"Resource not found"`.
- Callers should not be aware of the difference between "does not exist" and "is deleted".

---

## Summary Table

| Entity           | Required Fields                              | Key Duplicates          | Key Enums                                | Key Business Rules                              |
|------------------|----------------------------------------------|-------------------------|------------------------------------------|-------------------------------------------------|
| User / Auth      | firstName, lastName, email, password, role  | email (global)          | role (3 self-reg values)                 | ADMIN cannot self-register; email case-insensitive |
| Company          | name, industry                               | name (global)           | size (6 values)                          | Slug auto-generated; cannot delete with OPEN jobs |
| Job              | companyId, title, description, type, level, openings | — | type (4), level (5), status (3)       | Always starts DRAFT; forward-only status machine |
| Candidate        | (via user signup)                            | user_id (1:1)           | proficiencyLevel (4) on skills          | Max 50 skills; yearsOfExperience 0–50           |
| Application      | jobId, resumeId                              | (candidateId, jobId)    | status (6)                               | OPEN jobs only; forward-only status machine; terminal states |
| Resume           | file (PDF), candidateId                      | —                       | —                                        | Max 5 MB; max 10 per candidate; PDF only        |
| Resume Analysis  | jobId                                        | (resumeId, jobId)       | —                                        | Cached by default; score clamped 0–100          |
| Skill            | name                                         | name (case-insensitive) | —                                        | Cannot delete if referenced                     |
| Notification     | recipientUserId, type, title, message        | —                       | type (7 values)                          | System-generated only; own records only         |
| Activity Log     | action, entityType, createdAt               | —                       | —                                        | Immutable; never deleted                        |

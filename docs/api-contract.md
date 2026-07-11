# TalentOS — REST API Contract

> Version: 1.0
> Phase: System Design — API Contract
> Backend: Java 21 · Spring Boot 3 · Spring Security · JWT
> Base URL: `https://api.talentos.app/api/v1`
> Content-Type: `application/json`

---

## Conventions

### Standard Response Envelope

Every response is wrapped in a consistent envelope.

**Success**
```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... },
  "timestamp": "2026-07-11T08:30:00Z"
}
```

**Paginated Success**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 10,
    "totalElements": 84,
    "totalPages": 9,
    "last": false
  },
  "timestamp": "2026-07-11T08:30:00Z"
}
```

**Error**
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "email": "must be a valid email address",
    "password": "must be at least 8 characters"
  },
  "timestamp": "2026-07-11T08:30:00Z"
}
```

### Authentication

All protected endpoints require:
```
Authorization: Bearer <JWT>
```

JWT payload contains: `sub` (user UUID), `email`, `role`, `iat`, `exp`.

### Roles

| Role              | Description                          |
|-------------------|--------------------------------------|
| `ADMIN`           | Full platform access                 |
| `RECRUITER`       | Manage jobs, companies, applications |
| `HIRING_MANAGER`  | Review candidates, give feedback     |
| `CANDIDATE`       | Apply to jobs, manage own profile    |

### Common Status Codes

| Code | Meaning                                           |
|------|---------------------------------------------------|
| 200  | OK — read / update succeeded                      |
| 201  | Created — resource created                        |
| 204  | No Content — delete succeeded                     |
| 400  | Bad Request — validation failure                  |
| 401  | Unauthorized — missing or invalid JWT             |
| 403  | Forbidden — role does not have access             |
| 404  | Not Found — resource does not exist               |
| 409  | Conflict — duplicate resource (e.g. email exists) |
| 422  | Unprocessable Entity — business rule violation    |
| 500  | Internal Server Error                             |

---

## 1. Authentication

Base path: `/auth`
No JWT required for login and signup.

---

### POST /auth/signup

**Description:** Register a new user account. Creates the user and the appropriate role-specific profile record.

**Auth Required:** No

**Role Required:** None

**Request Body:**
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane@example.com",
  "password": "SecurePass123!",
  "role": "CANDIDATE"
}
```

**Validation Notes:**
- `firstName` — required, 1–50 chars
- `lastName` — required, 1–50 chars
- `email` — required, valid email format, must be unique
- `password` — required, min 8 chars, must contain at least one uppercase, one digit
- `role` — required, one of: `RECRUITER`, `HIRING_MANAGER`, `CANDIDATE` (ADMIN accounts cannot self-register)

**Response Body (201):**
```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "token": "<JWT>",
    "user": {
      "id": "uuid",
      "firstName": "Jane",
      "lastName": "Doe",
      "email": "jane@example.com",
      "role": "CANDIDATE",
      "createdAt": "2026-07-11T08:30:00Z"
    }
  }
}
```

**Status Codes:** 201, 400, 409

---

### POST /auth/login

**Description:** Authenticate with email and password. Returns a JWT token.

**Auth Required:** No

**Role Required:** None

**Request Body:**
```json
{
  "email": "jane@example.com",
  "password": "SecurePass123!"
}
```

**Validation Notes:**
- `email` — required, valid email format
- `password` — required

**Response Body (200):**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "<JWT>",
    "user": {
      "id": "uuid",
      "firstName": "Jane",
      "lastName": "Doe",
      "email": "jane@example.com",
      "role": "CANDIDATE",
      "avatarUrl": "https://..."
    }
  }
}
```

**Status Codes:** 200, 400, 401

---

### POST /auth/logout

**Description:** Invalidates the token on the client. No server-side state is maintained (stateless JWT). Frontend is responsible for clearing the stored token.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Request Body:** None

**Response Body (200):**
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null
}
```

**Status Codes:** 200, 401

---

### GET /auth/me

**Description:** Returns the authenticated user's identity and profile data. Used on app load to hydrate the frontend auth context.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Request Body:** None

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane@example.com",
    "role": "RECRUITER",
    "avatarUrl": "https://...",
    "isActive": true,
    "createdAt": "2026-07-11T08:00:00Z"
  }
}
```

**Status Codes:** 200, 401

---

## 2. Companies

Base path: `/companies`

---

### GET /companies

**Description:** Paginated list of all active companies. Supports search and filtering.

**Auth Required:** Yes

**Role Required:** `ADMIN`, `RECRUITER`, `HIRING_MANAGER`

**Query Parameters:**

| Param    | Type    | Default | Description                            |
|----------|---------|---------|----------------------------------------|
| page     | integer | 0       | Zero-based page number                 |
| size     | integer | 10      | Records per page (max 50)              |
| search   | string  | —       | Full-text search on name/description   |
| industry | string  | —       | Filter by industry                     |
| sort     | string  | `name,asc` | Sort field and direction            |

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "name": "Acme Corp",
        "slug": "acme-corp",
        "industry": "Technology",
        "size": "51-200",
        "location": "Bangalore, India",
        "logoUrl": "https://...",
        "websiteUrl": "https://acme.com",
        "jobCount": 5,
        "createdAt": "2026-07-01T00:00:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 24,
    "totalPages": 3,
    "last": false
  }
}
```

**Status Codes:** 200, 401, 403

---

### POST /companies

**Description:** Create a new company.

**Auth Required:** Yes

**Role Required:** `ADMIN`, `RECRUITER`

**Request Body:**
```json
{
  "name": "Acme Corp",
  "description": "A technology company...",
  "industry": "Technology",
  "size": "51-200",
  "websiteUrl": "https://acme.com",
  "logoUrl": "https://...",
  "location": "Bangalore, India"
}
```

**Validation Notes:**
- `name` — required, 2–100 chars, must be unique
- `industry` — required
- `size` — one of: `1-10`, `11-50`, `51-200`, `201-500`, `501-1000`, `1000+`
- `websiteUrl` — optional, valid URL format
- `description` — optional, max 2000 chars

**Response Body (201):**
```json
{
  "success": true,
  "message": "Company created successfully",
  "data": {
    "id": "uuid",
    "name": "Acme Corp",
    "slug": "acme-corp",
    "industry": "Technology",
    "size": "51-200",
    "location": "Bangalore, India",
    "websiteUrl": "https://acme.com",
    "logoUrl": "https://...",
    "description": "A technology company...",
    "createdAt": "2026-07-11T08:30:00Z",
    "createdBy": "uuid"
  }
}
```

**Status Codes:** 201, 400, 401, 403, 409

---

### GET /companies/{id}

**Description:** Get a single company by UUID.

**Auth Required:** Yes

**Role Required:** `ADMIN`, `RECRUITER`, `HIRING_MANAGER`

**Path Parameter:** `id` — company UUID

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "Acme Corp",
    "slug": "acme-corp",
    "description": "...",
    "industry": "Technology",
    "size": "51-200",
    "location": "Bangalore, India",
    "websiteUrl": "https://acme.com",
    "logoUrl": "https://...",
    "recruiterCount": 3,
    "jobCount": 5,
    "openJobCount": 2,
    "createdAt": "2026-07-01T00:00:00Z",
    "updatedAt": "2026-07-10T12:00:00Z"
  }
}
```

**Status Codes:** 200, 401, 403, 404

---

### PUT /companies/{id}

**Description:** Update company details. Full replacement of mutable fields.

**Auth Required:** Yes

**Role Required:** `ADMIN`, `RECRUITER`

**Path Parameter:** `id` — company UUID

**Request Body:** Same structure as POST `/companies`

**Response Body (200):**
```json
{
  "success": true,
  "message": "Company updated successfully",
  "data": { ... }
}
```

**Status Codes:** 200, 400, 401, 403, 404

---

### DELETE /companies/{id}

**Description:** Soft-delete a company. Fails if the company has active (OPEN) jobs.

**Auth Required:** Yes

**Role Required:** `ADMIN`

**Path Parameter:** `id` — company UUID

**Response Body (200):**
```json
{
  "success": true,
  "message": "Company deleted successfully",
  "data": null
}
```

**Status Codes:** 200, 401, 403, 404, 422

---

## 3. Jobs

Base path: `/jobs`

---

### GET /jobs

**Description:** Paginated job listing with search, filtering, and sorting. Candidates see only `OPEN` jobs. Recruiters and Admins see all statuses.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Query Parameters:**

| Param      | Type    | Default       | Description                                    |
|------------|---------|---------------|------------------------------------------------|
| page       | integer | 0             | Zero-based page number                         |
| size       | integer | 10            | Records per page (max 50)                      |
| search     | string  | —             | Full-text search on title and description      |
| status     | string  | —             | Filter by status: `DRAFT`, `OPEN`, `CLOSED`    |
| companyId  | uuid    | —             | Filter by company                              |
| type       | string  | —             | `FULL_TIME`, `PART_TIME`, `CONTRACT`, `INTERNSHIP` |
| level      | string  | —             | `JUNIOR`, `MID`, `SENIOR`, `LEAD`, `EXECUTIVE` |
| location   | string  | —             | Partial match on location field                |
| isRemote   | boolean | —             | Filter remote-only jobs                        |
| sort       | string  | `createdAt,desc` | Sort field and direction                    |

**Response Body (200):** Paginated list of job summaries.
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "title": "Senior Backend Engineer",
        "company": {
          "id": "uuid",
          "name": "Acme Corp",
          "logoUrl": "https://..."
        },
        "type": "FULL_TIME",
        "level": "SENIOR",
        "location": "Bangalore, India",
        "isRemote": false,
        "salaryMin": 1800000,
        "salaryMax": 2500000,
        "currency": "INR",
        "status": "OPEN",
        "openings": 2,
        "applicationCount": 14,
        "skills": ["Java", "Spring Boot", "PostgreSQL"],
        "createdAt": "2026-07-05T00:00:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 42,
    "totalPages": 5,
    "last": false
  }
}
```

**Status Codes:** 200, 401

---

### POST /jobs

**Description:** Create a new job posting under a company. New jobs default to `DRAFT` status.

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `ADMIN`

**Request Body:**
```json
{
  "companyId": "uuid",
  "title": "Senior Backend Engineer",
  "description": "We are looking for...",
  "requirements": "5+ years of Java experience...",
  "type": "FULL_TIME",
  "level": "SENIOR",
  "location": "Bangalore, India",
  "isRemote": false,
  "salaryMin": 1800000,
  "salaryMax": 2500000,
  "currency": "INR",
  "openings": 2,
  "skillIds": ["uuid-java", "uuid-spring-boot"]
}
```

**Validation Notes:**
- `companyId` — required, must exist and be accessible by the recruiter
- `title` — required, 5–150 chars
- `description` — required, min 50 chars
- `type` — required, valid enum value
- `level` — required, valid enum value
- `openings` — required, min 1
- `salaryMin` — optional; if both provided, salaryMin <= salaryMax
- `skillIds` — optional, each must be a valid skill UUID

**Response Body (201):**
```json
{
  "success": true,
  "message": "Job created successfully",
  "data": {
    "id": "uuid",
    "title": "Senior Backend Engineer",
    "status": "DRAFT",
    "company": { "id": "uuid", "name": "Acme Corp" },
    "type": "FULL_TIME",
    "level": "SENIOR",
    "openings": 2,
    "skills": [
      { "id": "uuid", "name": "Java", "isRequired": true }
    ],
    "createdAt": "2026-07-11T08:30:00Z"
  }
}
```

**Status Codes:** 201, 400, 401, 403, 404

---

### GET /jobs/{id}

**Description:** Get full job details including company info and skill requirements.

**Auth Required:** Yes

**Role Required:** Any authenticated user (CANDIDATE sees only OPEN jobs)

**Path Parameter:** `id` — job UUID

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "title": "Senior Backend Engineer",
    "description": "We are looking for...",
    "requirements": "5+ years...",
    "status": "OPEN",
    "type": "FULL_TIME",
    "level": "SENIOR",
    "location": "Bangalore, India",
    "isRemote": false,
    "salaryMin": 1800000,
    "salaryMax": 2500000,
    "currency": "INR",
    "openings": 2,
    "company": {
      "id": "uuid",
      "name": "Acme Corp",
      "logoUrl": "https://...",
      "industry": "Technology",
      "location": "Bangalore, India"
    },
    "createdByRecruiter": {
      "id": "uuid",
      "firstName": "John",
      "lastName": "Smith"
    },
    "skills": [
      { "id": "uuid", "name": "Java", "isRequired": true },
      { "id": "uuid", "name": "Docker", "isRequired": false }
    ],
    "applicationCount": 14,
    "closedAt": null,
    "createdAt": "2026-07-05T00:00:00Z",
    "updatedAt": "2026-07-06T10:00:00Z"
  }
}
```

**Status Codes:** 200, 401, 403, 404

---

### PUT /jobs/{id}

**Description:** Update job details. Only the recruiter who created the job or an Admin can update it.

**Auth Required:** Yes

**Role Required:** `RECRUITER` (own jobs), `ADMIN`

**Path Parameter:** `id` — job UUID

**Request Body:** Same structure as POST `/jobs` (excluding `companyId`)

**Response Body (200):**
```json
{
  "success": true,
  "message": "Job updated successfully",
  "data": { ... }
}
```

**Status Codes:** 200, 400, 401, 403, 404

---

### PATCH /jobs/{id}/status

**Description:** Change job lifecycle status. Allowed transitions: `DRAFT → OPEN`, `OPEN → CLOSED`. Cannot reopen a closed job.

**Auth Required:** Yes

**Role Required:** `RECRUITER` (own jobs), `ADMIN`

**Path Parameter:** `id` — job UUID

**Request Body:**
```json
{
  "status": "OPEN"
}
```

**Validation Notes:**
- `status` — required, valid enum value
- Invalid transitions (e.g. `CLOSED → OPEN`) return 422

**Response Body (200):**
```json
{
  "success": true,
  "message": "Job status updated to OPEN",
  "data": {
    "id": "uuid",
    "status": "OPEN",
    "updatedAt": "2026-07-11T09:00:00Z"
  }
}
```

**Status Codes:** 200, 400, 401, 403, 404, 422

---

### DELETE /jobs/{id}

**Description:** Soft-delete a job. Cannot delete a job with active (non-REJECTED, non-HIRED) applications.

**Auth Required:** Yes

**Role Required:** `RECRUITER` (own jobs), `ADMIN`

**Path Parameter:** `id` — job UUID

**Response Body (200):**
```json
{
  "success": true,
  "message": "Job deleted successfully",
  "data": null
}
```

**Status Codes:** 200, 401, 403, 404, 422

---

### GET /jobs/{id}/applications

**Description:** List all applications for a specific job. Used in the pipeline board view.

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `HIRING_MANAGER`, `ADMIN`

**Path Parameter:** `id` — job UUID

**Query Parameters:**

| Param  | Type   | Default | Description                                            |
|--------|--------|---------|--------------------------------------------------------|
| status | string | —       | Filter by application status                           |
| page   | integer | 0      | Page number                                            |
| size   | integer | 20     | Records per page                                       |
| sort   | string | `createdAt,desc` | Sort field                                  |

**Response Body (200):** Paginated list of application summaries.

**Status Codes:** 200, 401, 403, 404

---

## 4. Candidates

Base path: `/candidates`

---

### GET /candidates

**Description:** Paginated list of candidate profiles. For Recruiters and Hiring Managers to browse candidates.

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `HIRING_MANAGER`, `ADMIN`

**Query Parameters:**

| Param              | Type    | Default       | Description                              |
|--------------------|---------|---------------|------------------------------------------|
| page               | integer | 0             | Page number                              |
| size               | integer | 10            | Records per page                         |
| search             | string  | —             | Full-text search on headline and summary |
| skills             | string  | —             | Comma-separated skill names to filter    |
| minExperience      | integer | —             | Minimum years of experience              |
| maxExperience      | integer | —             | Maximum years of experience              |
| location           | string  | —             | Partial match on location                |
| sort               | string  | `createdAt,desc` | Sort field                            |

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "user": {
          "id": "uuid",
          "firstName": "Jane",
          "lastName": "Doe",
          "avatarUrl": "https://..."
        },
        "headline": "Senior Backend Engineer",
        "location": "Bangalore, India",
        "yearsOfExperience": 6,
        "skills": ["Java", "Spring Boot", "PostgreSQL"],
        "hasResume": true,
        "applicationCount": 3,
        "createdAt": "2026-07-01T00:00:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 87,
    "totalPages": 9,
    "last": false
  }
}
```

**Status Codes:** 200, 401, 403

---

### GET /candidates/{id}

**Description:** Get a candidate's full profile including skills, experience, and resume metadata.

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `HIRING_MANAGER`, `ADMIN`, or the `CANDIDATE` themselves

**Path Parameter:** `id` — candidate UUID

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "user": {
      "id": "uuid",
      "firstName": "Jane",
      "lastName": "Doe",
      "email": "jane@example.com",
      "avatarUrl": "https://..."
    },
    "headline": "Senior Backend Engineer",
    "summary": "Passionate backend engineer with 6 years of experience...",
    "location": "Bangalore, India",
    "linkedinUrl": "https://linkedin.com/in/janedoe",
    "githubUrl": "https://github.com/janedoe",
    "portfolioUrl": "https://janedoe.dev",
    "yearsOfExperience": 6,
    "highestEducation": "B.Tech Computer Science",
    "fieldOfStudy": "Computer Science",
    "skills": [
      {
        "id": "uuid",
        "name": "Java",
        "proficiencyLevel": "EXPERT",
        "yearsOfExperience": 6
      }
    ],
    "primaryResume": {
      "id": "uuid",
      "fileName": "jane-doe-resume.pdf",
      "fileUrl": "https://...",
      "uploadedAt": "2026-07-10T08:00:00Z"
    },
    "latestAnalysis": {
      "id": "uuid",
      "matchScore": 84,
      "analysedAt": "2026-07-10T08:05:00Z"
    },
    "createdAt": "2026-07-01T00:00:00Z",
    "updatedAt": "2026-07-10T08:00:00Z"
  }
}
```

**Status Codes:** 200, 401, 403, 404

---

### PUT /candidates/{id}

**Description:** Update the candidate's profile. Only the candidate themselves or an Admin can update it.

**Auth Required:** Yes

**Role Required:** `CANDIDATE` (own profile), `ADMIN`

**Path Parameter:** `id` — candidate UUID

**Request Body:**
```json
{
  "headline": "Senior Backend Engineer",
  "summary": "Passionate backend engineer...",
  "location": "Bangalore, India",
  "linkedinUrl": "https://linkedin.com/in/janedoe",
  "githubUrl": "https://github.com/janedoe",
  "portfolioUrl": "https://janedoe.dev",
  "yearsOfExperience": 6,
  "highestEducation": "B.Tech Computer Science",
  "fieldOfStudy": "Computer Science"
}
```

**Validation Notes:**
- `headline` — optional, max 150 chars
- `summary` — optional, max 2000 chars
- `yearsOfExperience` — optional, min 0
- `linkedinUrl`, `githubUrl`, `portfolioUrl` — optional, valid URL format

**Response Body (200):**
```json
{
  "success": true,
  "message": "Profile updated successfully",
  "data": { ... }
}
```

**Status Codes:** 200, 400, 401, 403, 404

---

### POST /candidates/{id}/skills

**Description:** Add or update skills on a candidate profile. Replaces all existing skills.

**Auth Required:** Yes

**Role Required:** `CANDIDATE` (own profile), `ADMIN`

**Path Parameter:** `id` — candidate UUID

**Request Body:**
```json
{
  "skills": [
    {
      "skillId": "uuid",
      "proficiencyLevel": "EXPERT",
      "yearsOfExperience": 6
    },
    {
      "skillId": "uuid",
      "proficiencyLevel": "INTERMEDIATE",
      "yearsOfExperience": 2
    }
  ]
}
```

**Validation Notes:**
- Each `skillId` must exist in the `skills` table
- `proficiencyLevel` — one of: `BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `EXPERT`
- `yearsOfExperience` — min 0

**Response Body (200):**
```json
{
  "success": true,
  "message": "Skills updated successfully",
  "data": {
    "skills": [ ... ]
  }
}
```

**Status Codes:** 200, 400, 401, 403, 404

---

## 5. Applications

Base path: `/applications`

---

### POST /applications

**Description:** A candidate submits an application for an open job. A candidate can apply to each job only once.

**Auth Required:** Yes

**Role Required:** `CANDIDATE`

**Request Body:**
```json
{
  "jobId": "uuid",
  "resumeId": "uuid",
  "coverLetter": "I am excited to apply for this role because..."
}
```

**Validation Notes:**
- `jobId` — required, must be an OPEN job
- `resumeId` — required, must belong to the authenticated candidate
- `coverLetter` — optional, max 3000 chars
- Duplicate application (same candidate + job) returns 409

**Response Body (201):**
```json
{
  "success": true,
  "message": "Application submitted successfully",
  "data": {
    "id": "uuid",
    "job": {
      "id": "uuid",
      "title": "Senior Backend Engineer",
      "company": { "name": "Acme Corp" }
    },
    "status": "APPLIED",
    "submittedAt": "2026-07-11T09:00:00Z"
  }
}
```

**Status Codes:** 201, 400, 401, 403, 404, 409, 422

---

### GET /applications

**Description:** List applications. Candidates see only their own. Recruiters see applications for their jobs. Admins see all.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Query Parameters:**

| Param     | Type    | Default         | Description                                |
|-----------|---------|-----------------|--------------------------------------------|
| page      | integer | 0               | Page number                                |
| size      | integer | 10              | Records per page                           |
| jobId     | uuid    | —               | Filter by job (RECRUITER / ADMIN only)     |
| status    | string  | —               | Filter by pipeline status                  |
| sort      | string  | `createdAt,desc`| Sort field                                 |

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "candidate": {
          "id": "uuid",
          "firstName": "Jane",
          "lastName": "Doe",
          "avatarUrl": "https://...",
          "headline": "Senior Backend Engineer"
        },
        "job": {
          "id": "uuid",
          "title": "Senior Backend Engineer",
          "company": { "name": "Acme Corp" }
        },
        "status": "SCREENING",
        "matchScore": 84,
        "statusChangedAt": "2026-07-09T10:00:00Z",
        "submittedAt": "2026-07-08T08:00:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 38,
    "totalPages": 4,
    "last": false
  }
}
```

**Status Codes:** 200, 401

---

### GET /applications/{id}

**Description:** Get full details of a single application. Candidate can only fetch their own. Recruiter can only fetch applications for jobs they manage.

**Auth Required:** Yes

**Role Required:** `CANDIDATE` (own), `RECRUITER`, `HIRING_MANAGER`, `ADMIN`

**Path Parameter:** `id` — application UUID

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "status": "SCREENING",
    "coverLetter": "I am excited...",
    "recruiterNotes": "Strong candidate, proceed.",
    "hiringManagerFeedback": null,
    "statusChangedAt": "2026-07-09T10:00:00Z",
    "submittedAt": "2026-07-08T08:00:00Z",
    "candidate": {
      "id": "uuid",
      "firstName": "Jane",
      "lastName": "Doe",
      "headline": "Senior Backend Engineer",
      "yearsOfExperience": 6,
      "skills": ["Java", "Spring Boot"]
    },
    "job": {
      "id": "uuid",
      "title": "Senior Backend Engineer",
      "company": { "name": "Acme Corp" }
    },
    "resume": {
      "id": "uuid",
      "fileName": "jane-doe-resume.pdf",
      "fileUrl": "https://..."
    },
    "latestAnalysis": {
      "id": "uuid",
      "matchScore": 84,
      "aiSummary": "Strong backend engineer...",
      "analysedAt": "2026-07-10T08:05:00Z"
    },
    "reviewedByRecruiter": {
      "id": "uuid",
      "firstName": "John",
      "lastName": "Smith"
    },
    "assignedHiringManager": null
  }
}
```

**Status Codes:** 200, 401, 403, 404

---

### PATCH /applications/{id}/status

**Description:** Advance or change an application's pipeline status. Allowed transitions: `APPLIED → SCREENING → INTERVIEW → OFFER → HIRED` or `any → REJECTED`. Only recruiters can change status for their jobs; hiring managers can add feedback.

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `ADMIN`

**Path Parameter:** `id` — application UUID

**Request Body:**
```json
{
  "status": "SCREENING",
  "recruiterNotes": "Looks like a strong fit, let's proceed."
}
```

**Validation Notes:**
- `status` — required, valid enum value
- Invalid transitions return 422
- Cannot move back to a previous status (except to REJECTED from any stage)

**Response Body (200):**
```json
{
  "success": true,
  "message": "Application status updated to SCREENING",
  "data": {
    "id": "uuid",
    "status": "SCREENING",
    "statusChangedAt": "2026-07-11T09:30:00Z"
  }
}
```

**Status Codes:** 200, 400, 401, 403, 404, 422

---

### PATCH /applications/{id}/feedback

**Description:** Hiring manager adds feedback to an application. Can only be set when status is `INTERVIEW` or later.

**Auth Required:** Yes

**Role Required:** `HIRING_MANAGER`, `ADMIN`

**Path Parameter:** `id` — application UUID

**Request Body:**
```json
{
  "hiringManagerFeedback": "Excellent technical skills. Recommend for offer stage."
}
```

**Validation Notes:**
- `hiringManagerFeedback` — required, max 3000 chars

**Response Body (200):**
```json
{
  "success": true,
  "message": "Feedback submitted",
  "data": {
    "id": "uuid",
    "hiringManagerFeedback": "Excellent technical skills...",
    "updatedAt": "2026-07-11T10:00:00Z"
  }
}
```

**Status Codes:** 200, 400, 401, 403, 404, 422

---

### PATCH /applications/{id}/assign

**Description:** Recruiter assigns a hiring manager to an application for evaluation.

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `ADMIN`

**Path Parameter:** `id` — application UUID

**Request Body:**
```json
{
  "hiringManagerId": "uuid"
}
```

**Validation Notes:**
- `hiringManagerId` — required, must be a valid hiring manager UUID

**Response Body (200):**
```json
{
  "success": true,
  "message": "Hiring manager assigned",
  "data": {
    "id": "uuid",
    "assignedHiringManager": {
      "id": "uuid",
      "firstName": "Alice",
      "lastName": "Johnson"
    }
  }
}
```

**Status Codes:** 200, 400, 401, 403, 404

---

## 6. Resume & Resume Analysis

Base path: `/resumes`

---

### POST /resumes/upload

**Description:** Upload a resume PDF for a candidate. File is stored in Supabase Storage; metadata is persisted in the database. Optionally triggers AI analysis if `jobId` is provided.

**Auth Required:** Yes

**Role Required:** `CANDIDATE`, `RECRUITER` (on behalf of candidate), `ADMIN`

**Content-Type:** `multipart/form-data`

**Form Fields:**

| Field       | Type   | Required | Description                                   |
|-------------|--------|----------|-----------------------------------------------|
| file        | File   | Yes      | PDF file. Max size: 5MB                       |
| candidateId | UUID   | Yes      | The candidate this resume belongs to          |
| isPrimary   | boolean | No      | Default false. Set true to make this the active resume |
| jobId       | UUID   | No       | If provided, triggers AI analysis against this job |

**Validation Notes:**
- `file` — required, must be `application/pdf`, max 5MB
- `candidateId` — required, CANDIDATE can only upload for themselves
- Only one resume can be `is_primary = true` per candidate (database enforced via service logic)

**Response Body (201):**
```json
{
  "success": true,
  "message": "Resume uploaded successfully",
  "data": {
    "id": "uuid",
    "fileName": "jane-doe-resume.pdf",
    "fileUrl": "https://supabase.../jane-doe-resume.pdf",
    "fileSizeBytes": 204800,
    "isPrimary": true,
    "uploadedAt": "2026-07-11T08:30:00Z",
    "analysisTriggered": true
  }
}
```

**Status Codes:** 201, 400, 401, 403, 404

---

### GET /resumes/candidate/{candidateId}

**Description:** List all resumes uploaded by a specific candidate.

**Auth Required:** Yes

**Role Required:** `CANDIDATE` (own), `RECRUITER`, `HIRING_MANAGER`, `ADMIN`

**Path Parameter:** `candidateId` — candidate UUID

**Response Body (200):**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "fileName": "jane-doe-resume-v2.pdf",
      "fileUrl": "https://...",
      "fileSizeBytes": 204800,
      "isPrimary": true,
      "uploadedAt": "2026-07-10T08:00:00Z"
    }
  ]
}
```

**Status Codes:** 200, 401, 403, 404

---

### DELETE /resumes/{id}

**Description:** Soft-delete a resume and remove the file from Supabase Storage. Cannot delete the primary resume if the candidate has active applications.

**Auth Required:** Yes

**Role Required:** `CANDIDATE` (own), `ADMIN`

**Path Parameter:** `id` — resume UUID

**Response Body (200):**
```json
{
  "success": true,
  "message": "Resume deleted successfully",
  "data": null
}
```

**Status Codes:** 200, 401, 403, 404, 422

---

### POST /resumes/{id}/analyse

**Description:** Trigger AI analysis of a resume against a specific job using Google Gemini. If a cached analysis exists for this resume+job pair, the cached result is returned. Pass `force=true` to re-generate.

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `ADMIN`, `CANDIDATE` (own resume)

**Path Parameter:** `id` — resume UUID

**Request Body:**
```json
{
  "jobId": "uuid",
  "force": false
}
```

**Validation Notes:**
- `jobId` — required, must be a valid OPEN or CLOSED job
- `force` — optional, default false; if true, bypasses cache and calls Gemini

**Response Body (200 — cached or freshly generated):**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "resumeId": "uuid",
    "jobId": "uuid",
    "matchScore": 84,
    "matchingSkills": ["Java", "Spring Boot", "PostgreSQL"],
    "missingSkills": ["Docker", "Kubernetes"],
    "strengths": [
      "Strong backend engineering experience",
      "REST API expertise",
      "Database optimization skills"
    ],
    "risks": [
      "Limited cloud deployment experience"
    ],
    "aiSummary": "Candidate demonstrates strong backend engineering capabilities and closely aligns with the role requirements.",
    "interviewTopics": ["Spring Security", "Multithreading", "Database Indexing"],
    "learningRecommendations": ["Docker", "Kubernetes"],
    "geminiModelVersion": "gemini-1.5-pro",
    "fromCache": true,
    "analysedAt": "2026-07-10T08:05:00Z"
  }
}
```

**Status Codes:** 200, 400, 401, 403, 404, 500

---

### GET /resumes/{resumeId}/analysis

**Description:** Get all saved analyses for a specific resume (across different jobs).

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `HIRING_MANAGER`, `ADMIN`, `CANDIDATE` (own)

**Path Parameter:** `resumeId` — resume UUID

**Response Body (200):**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "jobId": "uuid",
      "jobTitle": "Senior Backend Engineer",
      "matchScore": 84,
      "fromCache": true,
      "analysedAt": "2026-07-10T08:05:00Z"
    }
  ]
}
```

**Status Codes:** 200, 401, 403, 404

---

### GET /resumes/analysis/{analysisId}

**Description:** Get the full detail of one specific AI analysis report.

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `HIRING_MANAGER`, `ADMIN`, `CANDIDATE` (own)

**Path Parameter:** `analysisId` — resume_analysis UUID

**Response Body (200):** Full analysis object (same structure as POST `/resumes/{id}/analyse`)

**Status Codes:** 200, 401, 403, 404

---

## 7. Skills (Reference Data)

Base path: `/skills`

---

### GET /skills

**Description:** List all skills. Supports search for autocomplete. Used when creating/editing candidate profiles and job postings.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Query Parameters:**

| Param    | Type   | Default | Description                          |
|----------|--------|---------|--------------------------------------|
| search   | string | —       | Partial name match (autocomplete)    |
| category | string | —       | Filter by skill category             |
| page     | integer | 0      | Page number                          |
| size     | integer | 50     | Records per page                     |

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "content": [
      { "id": "uuid", "name": "Java", "category": "Backend" },
      { "id": "uuid", "name": "Spring Boot", "category": "Backend" }
    ],
    "totalElements": 120
  }
}
```

**Status Codes:** 200, 401

---

### POST /skills

**Description:** Create a new skill in the master list. Skill names must be unique (case-insensitive).

**Auth Required:** Yes

**Role Required:** `ADMIN`, `RECRUITER`

**Request Body:**
```json
{
  "name": "Spring AI",
  "category": "Backend"
}
```

**Validation Notes:**
- `name` — required, 1–100 chars, must be unique (case-insensitive)
- `category` — optional, max 50 chars

**Response Body (201):**
```json
{
  "success": true,
  "data": { "id": "uuid", "name": "Spring AI", "category": "Backend" }
}
```

**Status Codes:** 201, 400, 401, 403, 409

---

## 8. Dashboard & Analytics

Base path: `/dashboard`

Analytics endpoints are read-only aggregates. No write operations.

---

### GET /dashboard/stats

**Description:** Returns high-level KPI statistics scoped to the authenticated user's role and context.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Response Body (200) — for RECRUITER:**
```json
{
  "success": true,
  "data": {
    "totalJobs": 12,
    "openJobs": 4,
    "totalApplications": 148,
    "newApplicationsThisWeek": 23,
    "shortlistedCandidates": 18,
    "hiredThisMonth": 3,
    "averageMatchScore": 71.4,
    "conversionRate": 12.5
  }
}
```

**Response Body (200) — for CANDIDATE:**
```json
{
  "success": true,
  "data": {
    "totalApplications": 5,
    "activeApplications": 3,
    "interviewsScheduled": 1,
    "offersReceived": 0,
    "averageMatchScore": 78.2,
    "profileCompleteness": 85
  }
}
```

**Response Body (200) — for ADMIN:**
```json
{
  "success": true,
  "data": {
    "totalUsers": 312,
    "totalCandidates": 256,
    "totalRecruiters": 42,
    "totalCompanies": 18,
    "totalJobs": 87,
    "totalApplications": 1430,
    "totalResumesAnalysed": 892,
    "newUsersThisWeek": 24
  }
}
```

**Status Codes:** 200, 401

---

### GET /dashboard/pipeline

**Description:** Returns application count grouped by pipeline status. Used for the pipeline chart on the recruiter dashboard.

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `HIRING_MANAGER`, `ADMIN`

**Query Parameters:**

| Param     | Type | Description                              |
|-----------|------|------------------------------------------|
| jobId     | uuid | Filter to a specific job (optional)      |
| companyId | uuid | Filter to a specific company (optional)  |

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "pipeline": [
      { "status": "APPLIED",    "count": 58 },
      { "status": "SCREENING",  "count": 24 },
      { "status": "INTERVIEW",  "count": 11 },
      { "status": "OFFER",      "count": 4  },
      { "status": "HIRED",      "count": 3  },
      { "status": "REJECTED",   "count": 48 }
    ],
    "totalActive": 96,
    "totalClosed": 51
  }
}
```

**Status Codes:** 200, 401, 403

---

### GET /dashboard/recent-activity

**Description:** Returns the most recent platform activity events for the authenticated user's context. Used in the activity feed widget.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Query Parameters:**

| Param | Type    | Default | Description            |
|-------|---------|---------|------------------------|
| limit | integer | 10      | Number of events (max 25) |

**Response Body (200):**
```json
{
  "success": true,
  "data": [
    {
      "action": "APPLICATION_STATUS_CHANGED",
      "entityType": "applications",
      "description": "Jane Doe moved to SCREENING for Senior Backend Engineer",
      "actorName": "John Smith (Recruiter)",
      "occurredAt": "2026-07-11T09:30:00Z"
    }
  ]
}
```

**Status Codes:** 200, 401

---

### GET /dashboard/top-candidates

**Description:** Returns the top-ranked candidates for a specific job, ordered by AI match score.

**Auth Required:** Yes

**Role Required:** `RECRUITER`, `HIRING_MANAGER`, `ADMIN`

**Query Parameters:**

| Param | Type    | Default | Description                  |
|-------|---------|---------|------------------------------|
| jobId | uuid    | —       | Required. The job to rank for |
| limit | integer | 10      | Number of results (max 25)    |

**Response Body (200):**
```json
{
  "success": true,
  "data": [
    {
      "applicationId": "uuid",
      "candidate": {
        "id": "uuid",
        "firstName": "Jane",
        "lastName": "Doe",
        "headline": "Senior Backend Engineer"
      },
      "matchScore": 91,
      "status": "SCREENING",
      "analysedAt": "2026-07-10T08:05:00Z"
    }
  ]
}
```

**Status Codes:** 200, 400, 401, 403

---

## 9. Notifications

Base path: `/notifications`

---

### GET /notifications

**Description:** List in-app notifications for the authenticated user. Unread notifications are returned first.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Query Parameters:**

| Param  | Type    | Default | Description                      |
|--------|---------|---------|----------------------------------|
| isRead | boolean | —       | Filter by read/unread status      |
| page   | integer | 0       | Page number                      |
| size   | integer | 20      | Records per page                  |

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "type": "STATUS_CHANGED",
        "title": "Application Update",
        "message": "Your application for Senior Backend Engineer has moved to Screening.",
        "isRead": false,
        "applicationId": "uuid",
        "createdAt": "2026-07-11T09:30:00Z"
      }
    ],
    "unreadCount": 3,
    "page": 0,
    "size": 20,
    "totalElements": 12
  }
}
```

**Status Codes:** 200, 401

---

### PATCH /notifications/{id}/read

**Description:** Mark a specific notification as read.

**Auth Required:** Yes

**Role Required:** Any authenticated user (own notifications only)

**Path Parameter:** `id` — notification UUID

**Response Body (200):**
```json
{
  "success": true,
  "message": "Notification marked as read",
  "data": {
    "id": "uuid",
    "isRead": true,
    "readAt": "2026-07-11T10:00:00Z"
  }
}
```

**Status Codes:** 200, 401, 403, 404

---

### PATCH /notifications/read-all

**Description:** Mark all unread notifications for the authenticated user as read.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Response Body (200):**
```json
{
  "success": true,
  "message": "All notifications marked as read",
  "data": {
    "updatedCount": 3
  }
}
```

**Status Codes:** 200, 401

---

### GET /notifications/unread-count

**Description:** Returns the count of unread notifications. Called frequently by the frontend to update the notification badge.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "unreadCount": 3
  }
}
```

**Status Codes:** 200, 401

---

### DELETE /notifications/{id}

**Description:** Soft-delete (dismiss) a notification.

**Auth Required:** Yes

**Role Required:** Any authenticated user (own notifications only)

**Path Parameter:** `id` — notification UUID

**Response Body (200):**
```json
{
  "success": true,
  "message": "Notification dismissed",
  "data": null
}
```

**Status Codes:** 200, 401, 403, 404

---

## 10. User Profile

Base path: `/users`

---

### GET /users/me

**Description:** Get the authenticated user's own full profile, including the role-specific profile data (candidate details, recruiter details, etc.).

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane@example.com",
    "role": "CANDIDATE",
    "avatarUrl": "https://...",
    "isActive": true,
    "profile": {
      "id": "uuid",
      "headline": "Senior Backend Engineer",
      "location": "Bangalore, India",
      "yearsOfExperience": 6
    },
    "createdAt": "2026-07-01T00:00:00Z",
    "updatedAt": "2026-07-10T08:00:00Z"
  }
}
```

**Status Codes:** 200, 401

---

### PUT /users/me

**Description:** Update the authenticated user's basic account details (name, avatar).

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Request Body:**
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "avatarUrl": "https://..."
}
```

**Validation Notes:**
- `firstName` — required, 1–50 chars
- `lastName` — required, 1–50 chars
- `avatarUrl` — optional, valid URL format

**Response Body (200):**
```json
{
  "success": true,
  "message": "Profile updated successfully",
  "data": {
    "id": "uuid",
    "firstName": "Jane",
    "lastName": "Doe",
    "avatarUrl": "https://...",
    "updatedAt": "2026-07-11T10:00:00Z"
  }
}
```

**Status Codes:** 200, 400, 401

---

### PATCH /users/me/password

**Description:** Change the authenticated user's password. Requires current password verification.

**Auth Required:** Yes

**Role Required:** Any authenticated user

**Request Body:**
```json
{
  "currentPassword": "OldPass123!",
  "newPassword": "NewSecurePass456!",
  "confirmPassword": "NewSecurePass456!"
}
```

**Validation Notes:**
- `currentPassword` — required; must match stored BCrypt hash
- `newPassword` — required, min 8 chars, must contain uppercase + digit
- `confirmPassword` — must match `newPassword`
- New password must differ from current password

**Response Body (200):**
```json
{
  "success": true,
  "message": "Password changed successfully",
  "data": null
}
```

**Status Codes:** 200, 400, 401, 422

---

### GET /users (Admin Only)

**Description:** Paginated list of all platform users. Admin user management view.

**Auth Required:** Yes

**Role Required:** `ADMIN`

**Query Parameters:**

| Param  | Type    | Default | Description                                    |
|--------|---------|---------|------------------------------------------------|
| page   | integer | 0       | Page number                                    |
| size   | integer | 20      | Records per page                               |
| role   | string  | —       | Filter by role                                 |
| search | string  | —       | Search by name or email                        |
| active | boolean | —       | Filter by active status                        |

**Response Body (200):**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "firstName": "Jane",
        "lastName": "Doe",
        "email": "jane@example.com",
        "role": "CANDIDATE",
        "isActive": true,
        "createdAt": "2026-07-01T00:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 312
  }
}
```

**Status Codes:** 200, 401, 403

---

### PATCH /users/{id}/status (Admin Only)

**Description:** Activate or deactivate a user account.

**Auth Required:** Yes

**Role Required:** `ADMIN`

**Path Parameter:** `id` — user UUID

**Request Body:**
```json
{
  "isActive": false
}
```

**Response Body (200):**
```json
{
  "success": true,
  "message": "User account deactivated",
  "data": {
    "id": "uuid",
    "isActive": false,
    "updatedAt": "2026-07-11T10:00:00Z"
  }
}
```

**Status Codes:** 200, 400, 401, 403, 404

---

## Summary

### Full Endpoint Index

| Method | URL                                          | Auth | Roles                               |
|--------|----------------------------------------------|------|-------------------------------------|
| POST   | /auth/signup                                 | No   | —                                   |
| POST   | /auth/login                                  | No   | —                                   |
| POST   | /auth/logout                                 | Yes  | Any                                 |
| GET    | /auth/me                                     | Yes  | Any                                 |
| GET    | /companies                                   | Yes  | ADMIN, RECRUITER, HIRING_MANAGER    |
| POST   | /companies                                   | Yes  | ADMIN, RECRUITER                    |
| GET    | /companies/{id}                              | Yes  | ADMIN, RECRUITER, HIRING_MANAGER    |
| PUT    | /companies/{id}                              | Yes  | ADMIN, RECRUITER                    |
| DELETE | /companies/{id}                              | Yes  | ADMIN                               |
| GET    | /jobs                                        | Yes  | Any                                 |
| POST   | /jobs                                        | Yes  | ADMIN, RECRUITER                    |
| GET    | /jobs/{id}                                   | Yes  | Any                                 |
| PUT    | /jobs/{id}                                   | Yes  | ADMIN, RECRUITER (own)              |
| PATCH  | /jobs/{id}/status                            | Yes  | ADMIN, RECRUITER (own)              |
| DELETE | /jobs/{id}                                   | Yes  | ADMIN, RECRUITER (own)              |
| GET    | /jobs/{id}/applications                      | Yes  | ADMIN, RECRUITER, HIRING_MANAGER    |
| GET    | /candidates                                  | Yes  | ADMIN, RECRUITER, HIRING_MANAGER    |
| GET    | /candidates/{id}                             | Yes  | ADMIN, RECRUITER, HM, CANDIDATE (own)|
| PUT    | /candidates/{id}                             | Yes  | ADMIN, CANDIDATE (own)              |
| POST   | /candidates/{id}/skills                      | Yes  | ADMIN, CANDIDATE (own)              |
| POST   | /applications                                | Yes  | CANDIDATE                           |
| GET    | /applications                                | Yes  | Any                                 |
| GET    | /applications/{id}                           | Yes  | Any (scoped)                        |
| PATCH  | /applications/{id}/status                    | Yes  | ADMIN, RECRUITER                    |
| PATCH  | /applications/{id}/feedback                  | Yes  | ADMIN, HIRING_MANAGER               |
| PATCH  | /applications/{id}/assign                    | Yes  | ADMIN, RECRUITER                    |
| POST   | /resumes/upload                              | Yes  | ADMIN, RECRUITER, CANDIDATE         |
| GET    | /resumes/candidate/{candidateId}             | Yes  | Any (scoped)                        |
| DELETE | /resumes/{id}                                | Yes  | ADMIN, CANDIDATE (own)              |
| POST   | /resumes/{id}/analyse                        | Yes  | ADMIN, RECRUITER, CANDIDATE (own)   |
| GET    | /resumes/{resumeId}/analysis                 | Yes  | ADMIN, RECRUITER, HM, CANDIDATE (own)|
| GET    | /resumes/analysis/{analysisId}               | Yes  | ADMIN, RECRUITER, HM, CANDIDATE (own)|
| GET    | /skills                                      | Yes  | Any                                 |
| POST   | /skills                                      | Yes  | ADMIN, RECRUITER                    |
| GET    | /dashboard/stats                             | Yes  | Any                                 |
| GET    | /dashboard/pipeline                          | Yes  | ADMIN, RECRUITER, HIRING_MANAGER    |
| GET    | /dashboard/recent-activity                   | Yes  | Any                                 |
| GET    | /dashboard/top-candidates                    | Yes  | ADMIN, RECRUITER, HIRING_MANAGER    |
| GET    | /notifications                               | Yes  | Any                                 |
| PATCH  | /notifications/{id}/read                     | Yes  | Any (own)                           |
| PATCH  | /notifications/read-all                      | Yes  | Any                                 |
| GET    | /notifications/unread-count                  | Yes  | Any                                 |
| DELETE | /notifications/{id}                          | Yes  | Any (own)                           |
| GET    | /users/me                                    | Yes  | Any                                 |
| PUT    | /users/me                                    | Yes  | Any                                 |
| PATCH  | /users/me/password                           | Yes  | Any                                 |
| GET    | /users                                       | Yes  | ADMIN                               |
| PATCH  | /users/{id}/status                           | Yes  | ADMIN                               |

**Total: 46 endpoints across 10 resource groups.**

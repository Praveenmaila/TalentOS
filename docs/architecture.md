# TalentOS — Software Architecture

> Version: 1.0
> Phase: System Design — MVP
> Architecture: Modular Monolith
> Stack: Java 21 · Spring Boot 3 · PostgreSQL · Next.js 16

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          CLIENTS                                │
│                                                                 │
│   Browser (Recruiter / Hiring Manager / Candidate / Admin)     │
└──────────────────────────┬──────────────────────────────────────┘
                           │  HTTPS
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                     FRONTEND — Vercel                            │
│                                                                  │
│   Next.js 16 (App Router)                                        │
│   TypeScript · Tailwind CSS · shadcn/ui                          │
│   TanStack Query · React Hook Form · Zod                         │
│                                                                  │
│   ┌──────────────┐  ┌─────────────────┐  ┌───────────────────┐  │
│   │  Auth Pages  │  │  Role Dashboards │  │  Feature Pages    │  │
│   └──────────────┘  └─────────────────┘  └───────────────────┘  │
└──────────────────────────┬───────────────────────────────────────┘
                           │  REST / JSON   (JWT Bearer)
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    BACKEND — Railway                              │
│                                                                  │
│   Spring Boot 3 (Modular Monolith)                               │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    API Layer (REST)                          │ │
│  │   Spring MVC Controllers · OpenAPI · Global Exception Handler│ │
│  └───────────────────────┬─────────────────────────────────────┘ │
│                          │                                        │
│  ┌───────────────────────▼─────────────────────────────────────┐ │
│  │               Domain Modules (Internal)                      │ │
│  │                                                              │ │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌──────────┐ ┌────────┐  │ │
│  │  │  auth  │ │company │ │  job   │ │candidate │ │  app   │  │ │
│  │  └────────┘ └────────┘ └────────┘ └──────────┘ └────────┘  │ │
│  │                                                              │ │
│  │  ┌──────────────┐ ┌────────────────┐ ┌──────────────────┐  │ │
│  │  │   resume     │ │   analytics    │ │  notification    │  │ │
│  │  │ intelligence │ │   dashboard    │ │  (in-app only)   │  │ │
│  │  └──────────────┘ └────────────────┘ └──────────────────┘  │ │
│  └───────────────────────┬─────────────────────────────────────┘ │
│                          │                                        │
│  ┌───────────────────────▼─────────────────────────────────────┐ │
│  │                  Shared Kernel                               │ │
│  │   Security · DTOs · Exceptions · Config · Utils             │ │
│  └───────────────────────┬─────────────────────────────────────┘ │
│                          │                                        │
│  ┌───────────────────────▼─────────────────────────────────────┐ │
│  │             External Service Clients                         │ │
│  │                                                              │ │
│  │   ┌─────────────────┐      ┌──────────────────────────┐     │ │
│  │   │ Google Gemini   │      │   Supabase Storage SDK   │     │ │
│  │   │  (AI Analysis)  │      │   (Resume File Upload)   │     │ │
│  │   └─────────────────┘      └──────────────────────────┘     │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────┬───────────────────────────────────────┘
                           │  JDBC / JPA
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                   DATABASE — Neon (PostgreSQL)                    │
│   Managed by Flyway migrations                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. Module Boundaries

Each module owns its own domain. Cross-module communication happens only through service interfaces — never by directly accessing another module's repository.

```
┌───────────────────────────────────────────────────────────────────┐
│                         MODULE MAP                                │
├────────────────────┬──────────────────────────────────────────────┤
│ Module             │ Responsibility                                │
├────────────────────┼──────────────────────────────────────────────┤
│ auth               │ Signup, Login, JWT issuance & validation,     │
│                    │ RBAC roles: ADMIN, RECRUITER, HIRING_MANAGER, │
│                    │ CANDIDATE                                     │
├────────────────────┼──────────────────────────────────────────────┤
│ company            │ Company profiles, create/update/search        │
│                    │ Owned by ADMIN / RECRUITER                    │
├────────────────────┼──────────────────────────────────────────────┤
│ job                │ Job postings, lifecycle status, search,       │
│                    │ filters, sorting, pagination                  │
├────────────────────┼──────────────────────────────────────────────┤
│ candidate          │ Candidate profiles, skills, experience,       │
│                    │ resume metadata, profile management           │
├────────────────────┼──────────────────────────────────────────────┤
│ application        │ Apply for jobs, pipeline stages,              │
│ (app)              │ status transitions, recruiter review          │
├────────────────────┼──────────────────────────────────────────────┤
│ resume             │ Resume file upload (Supabase), parsing,       │
│ intelligence       │ AI analysis via Gemini, skill extraction,     │
│                    │ candidate ranking, explainability report       │
├────────────────────┼──────────────────────────────────────────────┤
│ analytics          │ Aggregated hiring metrics, pipeline stats,    │
│                    │ dashboard data per role                       │
├────────────────────┼──────────────────────────────────────────────┤
│ notification       │ In-app notification records, read/unread      │
│                    │ status. No external email in MVP.             │
├────────────────────┼──────────────────────────────────────────────┤
│ user               │ User profile view/edit, avatar, account       │
│                    │ settings                                      │
└────────────────────┴──────────────────────────────────────────────┘
```

### Cross-Module Dependency Rules

```
           auth <---- all modules (for user/role context)
           candidate <---- application, resume
           job <---- application, analytics
           application <---- analytics, notification
           resume <---- analytics
```

- Modules import only from `shared` and from their direct upstream modules.
- No circular dependencies.
- No module accesses another module's `repository` package directly.

---

## 3. Backend Package Structure

```
backend/
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── talentos/
        │           │
        │           ├── TalentOsApplication.java          <- Spring Boot entry point
        │           │
        │           ├── shared/                           <- Shared Kernel
        │           │   ├── config/
        │           │   │   ├── SecurityConfig.java
        │           │   │   ├── JwtConfig.java
        │           │   │   ├── CorsConfig.java
        │           │   │   └── OpenApiConfig.java
        │           │   ├── exception/
        │           │   │   ├── GlobalExceptionHandler.java
        │           │   │   ├── ResourceNotFoundException.java
        │           │   │   ├── AccessDeniedException.java
        │           │   │   └── ValidationException.java
        │           │   ├── response/
        │           │   │   ├── ApiResponse.java           <- Standard response envelope
        │           │   │   └── PagedResponse.java
        │           │   ├── security/
        │           │   │   ├── JwtService.java
        │           │   │   ├── JwtAuthFilter.java
        │           │   │   └── UserPrincipal.java
        │           │   └── util/
        │           │       └── SlugUtils.java
        │           │
        │           ├── auth/                             <- Module: Authentication
        │           │   ├── controller/
        │           │   │   └── AuthController.java
        │           │   ├── dto/
        │           │   │   ├── LoginRequest.java
        │           │   │   ├── SignupRequest.java
        │           │   │   └── AuthResponse.java
        │           │   ├── model/
        │           │   │   ├── User.java
        │           │   │   └── Role.java                 <- Enum: ADMIN, RECRUITER,
        │           │   ├── repository/                       HIRING_MANAGER, CANDIDATE
        │           │   │   └── UserRepository.java
        │           │   └── service/
        │           │       └── AuthService.java
        │           │
        │           ├── company/                          <- Module: Company
        │           │   ├── controller/
        │           │   │   └── CompanyController.java
        │           │   ├── dto/
        │           │   │   ├── CompanyRequest.java
        │           │   │   └── CompanyResponse.java
        │           │   ├── model/
        │           │   │   └── Company.java
        │           │   ├── repository/
        │           │   │   └── CompanyRepository.java
        │           │   └── service/
        │           │       └── CompanyService.java
        │           │
        │           ├── job/                              <- Module: Job Management
        │           │   ├── controller/
        │           │   │   └── JobController.java
        │           │   ├── dto/
        │           │   │   ├── JobRequest.java
        │           │   │   ├── JobResponse.java
        │           │   │   └── JobSearchCriteria.java
        │           │   ├── model/
        │           │   │   ├── Job.java
        │           │   │   └── JobStatus.java            <- Enum: DRAFT, OPEN, CLOSED
        │           │   ├── repository/
        │           │   │   └── JobRepository.java
        │           │   └── service/
        │           │       └── JobService.java
        │           │
        │           ├── candidate/                        <- Module: Candidate Profile
        │           │   ├── controller/
        │           │   │   └── CandidateController.java
        │           │   ├── dto/
        │           │   │   ├── CandidateProfileRequest.java
        │           │   │   └── CandidateProfileResponse.java
        │           │   ├── model/
        │           │   │   ├── CandidateProfile.java
        │           │   │   ├── Skill.java
        │           │   │   └── Experience.java
        │           │   ├── repository/
        │           │   │   ├── CandidateProfileRepository.java
        │           │   │   └── SkillRepository.java
        │           │   └── service/
        │           │       └── CandidateService.java
        │           │
        │           ├── application/                      <- Module: Application Pipeline
        │           │   ├── controller/
        │           │   │   └── ApplicationController.java
        │           │   ├── dto/
        │           │   │   ├── ApplicationRequest.java
        │           │   │   ├── ApplicationResponse.java
        │           │   │   └── StatusUpdateRequest.java
        │           │   ├── model/
        │           │   │   ├── Application.java
        │           │   │   └── ApplicationStatus.java    <- Enum: APPLIED, SCREENING,
        │           │   ├── repository/                       INTERVIEW, OFFER, REJECTED,
        │           │   │   └── ApplicationRepository.java    HIRED
        │           │   └── service/
        │           │       └── ApplicationService.java
        │           │
        │           ├── resume/                           <- Module: Resume Intelligence
        │           │   ├── controller/
        │           │   │   └── ResumeController.java
        │           │   ├── dto/
        │           │   │   ├── ResumeUploadResponse.java
        │           │   │   └── ResumeAnalysisResponse.java
        │           │   ├── model/
        │           │   │   └── ResumeAnalysis.java
        │           │   ├── repository/
        │           │   │   └── ResumeAnalysisRepository.java
        │           │   ├── service/
        │           │   │   ├── ResumeStorageService.java  <- Supabase
        │           │   │   ├── ResumeParserService.java   <- Text extraction
        │           │   │   └── ResumeAiService.java       <- Gemini API
        │           │   └── client/
        │           │       ├── GeminiClient.java
        │           │       └── SupabaseStorageClient.java
        │           │
        │           ├── analytics/                        <- Module: Analytics/Dashboard
        │           │   ├── controller/
        │           │   │   └── AnalyticsController.java
        │           │   ├── dto/
        │           │   │   ├── DashboardStats.java
        │           │   │   └── PipelineStats.java
        │           │   └── service/
        │           │       └── AnalyticsService.java
        │           │
        │           ├── notification/                     <- Module: In-App Notifications
        │           │   ├── controller/
        │           │   │   └── NotificationController.java
        │           │   ├── dto/
        │           │   │   └── NotificationResponse.java
        │           │   ├── model/
        │           │   │   └── Notification.java
        │           │   ├── repository/
        │           │   │   └── NotificationRepository.java
        │           │   └── service/
        │           │       └── NotificationService.java
        │           │
        │           └── user/                             <- Module: User Profile
        │               ├── controller/
        │               │   └── UserController.java
        │               ├── dto/
        │               │   ├── UserProfileRequest.java
        │               │   └── UserProfileResponse.java
        │               └── service/
        │                   └── UserService.java
        │
        └── resources/
            ├── application.yml                           <- Main config
            ├── application-local.yml                     <- Local overrides (git-ignored)
            └── db/
                └── migration/                            <- Flyway SQL migrations
                    ├── V1__init_users.sql
                    ├── V2__init_companies.sql
                    ├── V3__init_jobs.sql
                    ├── V4__init_candidates.sql
                    ├── V5__init_applications.sql
                    ├── V6__init_resume_analysis.sql
                    └── V7__init_notifications.sql
```

---

## 4. Frontend Folder Structure

```
frontend/
├── public/
│   └── favicon.ico
│
├── src/
│   ├── app/                                <- Next.js App Router
│   │   │
│   │   ├── (auth)/                         <- Route group: unauthenticated
│   │   │   ├── login/
│   │   │   │   └── page.tsx
│   │   │   └── signup/
│   │   │       └── page.tsx
│   │   │
│   │   ├── (dashboard)/                    <- Route group: authenticated
│   │   │   ├── layout.tsx                  <- Shell with sidebar + topbar
│   │   │   │
│   │   │   ├── dashboard/
│   │   │   │   └── page.tsx                <- Role-aware dashboard home
│   │   │   │
│   │   │   ├── jobs/
│   │   │   │   ├── page.tsx                <- Job listing + search
│   │   │   │   ├── new/
│   │   │   │   │   └── page.tsx            <- Create job (RECRUITER)
│   │   │   │   └── [id]/
│   │   │   │       ├── page.tsx            <- Job detail
│   │   │   │       └── edit/
│   │   │   │           └── page.tsx
│   │   │   │
│   │   │   ├── candidates/
│   │   │   │   ├── page.tsx                <- Candidate list (RECRUITER / HM)
│   │   │   │   └── [id]/
│   │   │   │       └── page.tsx            <- Candidate profile + AI report
│   │   │   │
│   │   │   ├── applications/
│   │   │   │   ├── page.tsx                <- Pipeline board / list
│   │   │   │   └── [id]/
│   │   │   │       └── page.tsx            <- Application detail
│   │   │   │
│   │   │   ├── companies/
│   │   │   │   ├── page.tsx
│   │   │   │   └── [id]/
│   │   │   │       └── page.tsx
│   │   │   │
│   │   │   ├── notifications/
│   │   │   │   └── page.tsx
│   │   │   │
│   │   │   └── profile/
│   │   │       └── page.tsx
│   │   │
│   │   ├── layout.tsx                      <- Root layout (fonts, providers)
│   │   ├── page.tsx                        <- Landing / redirect
│   │   └── not-found.tsx
│   │
│   ├── components/
│   │   ├── ui/                             <- shadcn/ui primitives
│   │   │   ├── button.tsx
│   │   │   ├── input.tsx
│   │   │   ├── badge.tsx
│   │   │   ├── card.tsx
│   │   │   ├── dialog.tsx
│   │   │   ├── table.tsx
│   │   │   ├── select.tsx
│   │   │   ├── avatar.tsx
│   │   │   └── ...
│   │   │
│   │   ├── layout/                         <- Shell components
│   │   │   ├── Sidebar.tsx
│   │   │   ├── Topbar.tsx
│   │   │   └── PageHeader.tsx
│   │   │
│   │   ├── auth/
│   │   │   ├── LoginForm.tsx
│   │   │   └── SignupForm.tsx
│   │   │
│   │   ├── job/
│   │   │   ├── JobCard.tsx
│   │   │   ├── JobForm.tsx
│   │   │   ├── JobStatusBadge.tsx
│   │   │   └── JobFilters.tsx
│   │   │
│   │   ├── candidate/
│   │   │   ├── CandidateCard.tsx
│   │   │   ├── CandidateProfile.tsx
│   │   │   └── SkillBadge.tsx
│   │   │
│   │   ├── application/
│   │   │   ├── PipelineBoard.tsx
│   │   │   ├── ApplicationCard.tsx
│   │   │   └── StatusSelect.tsx
│   │   │
│   │   ├── resume/
│   │   │   ├── ResumeUpload.tsx
│   │   │   └── AiAnalysisReport.tsx        <- Explainability card
│   │   │
│   │   ├── dashboard/
│   │   │   ├── StatsCard.tsx
│   │   │   ├── PipelineChart.tsx
│   │   │   └── RecentActivity.tsx
│   │   │
│   │   └── shared/
│   │       ├── DataTable.tsx               <- Reusable paginated table
│   │       ├── SearchInput.tsx
│   │       ├── Pagination.tsx
│   │       ├── EmptyState.tsx
│   │       ├── LoadingSpinner.tsx
│   │       └── ConfirmDialog.tsx
│   │
│   ├── hooks/                              <- Custom React hooks
│   │   ├── useAuth.ts
│   │   ├── useJobs.ts
│   │   ├── useCandidates.ts
│   │   ├── useApplications.ts
│   │   └── useDashboard.ts
│   │
│   ├── lib/
│   │   ├── api/                            <- Typed API clients (fetch wrappers)
│   │   │   ├── client.ts                   <- Base fetch with JWT injection
│   │   │   ├── auth.api.ts
│   │   │   ├── jobs.api.ts
│   │   │   ├── candidates.api.ts
│   │   │   ├── applications.api.ts
│   │   │   ├── resume.api.ts
│   │   │   └── analytics.api.ts
│   │   ├── auth.ts                         <- Token storage helpers
│   │   └── utils.ts
│   │
│   ├── providers/
│   │   ├── AuthProvider.tsx                <- Auth context + token management
│   │   └── QueryProvider.tsx               <- TanStack Query client
│   │
│   ├── types/                              <- Shared TypeScript types
│   │   ├── auth.types.ts
│   │   ├── job.types.ts
│   │   ├── candidate.types.ts
│   │   ├── application.types.ts
│   │   └── api.types.ts
│   │
│   └── middleware.ts                       <- Next.js route protection
│
├── .env.local                              <- git-ignored
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── package.json
```

---

## 5. Request Flow

### Standard CRUD Request (e.g., List Jobs with Search & Pagination)

```
Browser
  |
  +- 1. User types search query, selects filters
  |
  v
Next.js Page Component
  |
  +- 2. useJobs() hook triggers TanStack Query
  |
  v
jobs.api.ts (lib/api)
  |
  +- 3. GET /api/v1/jobs?title=java&status=OPEN&page=0&size=10
  |      Authorization: Bearer <JWT>
  |
  v
Spring Boot — JwtAuthFilter
  |
  +- 4. Validate JWT, extract user principal & roles
  |
  v
JobController
  |
  +- 5. Receive request, validate query params
  |
  v
JobService
  |
  +- 6. Build JPA Specification from criteria
  +- 7. Query JobRepository (Spring Data JPA + Specifications)
  |
  v
PostgreSQL (Neon)
  |
  +- 8. Execute parameterised SQL, return rows
  |
  v
JobService
  |
  +- 9. Map Job entities -> JobResponse DTOs (MapStruct)
  +- 10. Wrap in PagedResponse<JobResponse>
  |
  v
JobController -> ApiResponse envelope -> HTTP 200
  |
  v
Next.js — TanStack Query caches response
  |
  v
Browser renders job listing with pagination controls
```

### Resume Upload & AI Analysis Flow

```
Browser (Candidate or Recruiter)
  |
  +- 1. User selects PDF resume, submits form
  |
  v
ResumeUpload.tsx -> POST /api/v1/resumes/upload (multipart/form-data)
  |
  v
ResumeController -> ResumeStorageService
  |
  +- 2. Stream file to Supabase Storage
  +- 3. Receive public URL back
  |
  v
ResumeParserService
  |
  +- 4. Extract raw text from PDF (Apache PDFBox)
  |
  v
ResumeAiService -> GeminiClient
  |
  +- 5. Build structured prompt with:
  |      - Extracted resume text
  |      - Job description (if analyzing against a job)
  +- 6. Call Google Gemini API
  +- 7. Parse JSON response into ResumeAnalysis model
  |
  v
ResumeAnalysisRepository -> PostgreSQL
  |
  +- 8. Persist analysis result
  |
  v
ResumeController -> ResumeAnalysisResponse -> HTTP 201
  |
  v
Browser: AiAnalysisReport.tsx renders
  - Overall match %
  - Matching Skills
  - Missing Skills
  - Strengths
  - Risks
  - AI Summary
  - Interview Topics
  - Learning Recommendations
```

---

## 6. Authentication Flow

### Signup

```
Browser -> POST /api/v1/auth/signup
  { name, email, password, role }
     |
     v
AuthController -> AuthService
  +- Validate request (Bean Validation)
  +- Check email uniqueness
  +- Hash password (BCrypt)
  +- Save User to DB
  +- Generate JWT
     |
     v
Response: { token, user: { id, name, email, role } }
     |
     v
Frontend: Store token in httpOnly cookie (via Next.js middleware)
AuthProvider updates context
Redirect to /dashboard
```

### Login

```
Browser -> POST /api/v1/auth/login
  { email, password }
     |
     v
AuthController -> AuthService
  +- Load user by email
  +- BCrypt.matches(rawPassword, storedHash)
  +- Generate JWT (subject=userId, claims: role)
  +- Return AuthResponse
     |
     v
Frontend: Token stored, AuthProvider hydrates user context
Redirect to role-specific dashboard
```

### JWT Token Structure

```
Header:  { alg: HS256, typ: JWT }
Payload: {
  sub:   "<user-id>",
  email: "<user@email.com>",
  role:  "RECRUITER",
  iat:   <issued-at>,
  exp:   <expiry: 7 days>
}
```

### Request Authentication

```
Every protected API request:
  1. JwtAuthFilter intercepts request
  2. Extract Bearer token from Authorization header
  3. JwtService validates signature + expiry
  4. Set SecurityContextHolder with UserPrincipal
  5. Controller method-level @PreAuthorize checks role

Roles & Permissions:
  ADMIN          -> full access
  RECRUITER      -> manage jobs, companies, view applications, run AI analysis
  HIRING_MANAGER -> view shortlisted candidates, add feedback
  CANDIDATE      -> own profile, upload resume, apply to jobs, view own applications
```

### Route Protection (Frontend)

```
Next.js middleware.ts
  +- Read token from cookie
  +- If no token -> redirect to /login
  +- Decode role from token payload (no verification — backend verifies)
  +- Role-gated routes:
       /jobs/new         -> RECRUITER only
       /candidates       -> RECRUITER, HIRING_MANAGER
       /applications     -> RECRUITER, HIRING_MANAGER, CANDIDATE (own)
       /dashboard/admin  -> ADMIN only
```

---

## 7. Design Decisions

### DD-01 — Modular Monolith over Microservices

**Decision:** All domain modules live inside a single Spring Boot application.

**Rationale:** With one developer and a 7-day MVP window, the operational overhead of microservices (service discovery, inter-service auth, distributed tracing) would consume more time than the product itself. A modular monolith delivers clean boundaries now and can be extracted later if needed.

---

### DD-02 — JWT Stored in HttpOnly Cookie

**Decision:** Store JWT in an httpOnly cookie managed by Next.js middleware rather than localStorage.

**Rationale:** localStorage is vulnerable to XSS. HttpOnly cookies prevent JavaScript from reading the token. Next.js middleware can read cookies server-side to protect routes without exposing the token to client code.

---

### DD-03 — Spring Data JPA Specifications for Search & Filtering

**Decision:** Use `JpaSpecificationExecutor` with dynamic `Specification<T>` builders for job and candidate search.

**Rationale:** This avoids raw SQL strings, keeps queries type-safe, and supports composable filter criteria without maintaining multiple custom query methods for every combination of filters.

---

### DD-04 — Flyway for Database Migrations

**Decision:** All schema changes managed through Flyway versioned SQL migrations.

**Rationale:** Reproducible schema across local, CI, and production environments. Zero manual DB intervention. Neon (PostgreSQL) is compatible with Flyway out of the box.

---

### DD-05 — AI Analysis Stored in Database

**Decision:** Every Gemini AI analysis result is persisted in the `resume_analysis` table.

**Rationale:** AI calls are expensive (latency + cost). Storing results allows instant retrieval on subsequent views without re-calling Gemini. Results can be re-generated on-demand when the job description changes.

---

### DD-06 — Supabase Storage for Resume Files

**Decision:** Use Supabase Storage for resume file uploads instead of storing files in PostgreSQL or the local filesystem.

**Rationale:** Managed object storage with a generous free tier. Files are accessible via public URL. Railway's ephemeral filesystem makes local storage unreliable for deployments.

---

### DD-07 — No Redis / No Caching Layer

**Decision:** No caching infrastructure in the MVP.

**Rationale:** PostgreSQL with proper indexing is sufficient for MVP read volumes. Adding Redis increases operational complexity without measurable benefit at this scale.

---

### DD-08 — TanStack Query for Server State

**Decision:** All server data fetching, caching, and mutation handled by TanStack Query on the frontend.

**Rationale:** Eliminates manual loading/error state management. Provides automatic background refetching, cache invalidation after mutations, and pagination support — replacing what would otherwise require significant boilerplate.

---

### DD-09 — Role-Aware Dashboard (Single Route)

**Decision:** `/dashboard` is a single route that renders different widgets based on the user's role from `AuthProvider`.

**Rationale:** Avoids duplicating layout work across four separate dashboard routes. Component-level role checks keep the dashboard maintainable by one developer.

---

### DD-10 — OpenAPI / Swagger Auto-Generated Docs

**Decision:** Use `springdoc-openapi` to auto-generate interactive API docs from controller annotations.

**Rationale:** Zero manual documentation maintenance. The spec doubles as a contract for the frontend. Accessible at `/swagger-ui.html` during development.

---

## 8. Trade-offs

| Trade-off | Decision Made | What Was Sacrificed | Why Acceptable for MVP |
|---|---|---|---|
| Monolith vs Microservices | Monolith | Independent deployability per service | One developer, 7-day timeline |
| JWT Stateless vs Sessions | JWT | Instant token revocation | Acceptable for MVP; logout clears client token |
| No Redis | Skip caching | Reduced read performance at scale | PostgreSQL is sufficient at MVP traffic |
| AI results persisted | Store in DB | Slightly stale analysis if resume changes | Re-analyze button mitigates this |
| No OAuth | Email/password only | Google/GitHub login convenience | Reduces third-party dependencies |
| No email notifications | In-app only | Candidate receives no email updates | Excluded explicitly in Future Scope |
| No real-time updates | Polling / manual refresh | Live pipeline updates | Real-time is Future Scope |
| Single-process deployment | One JAR on Railway | Vertical scaling only | Fine for MVP; Railway scales vertically |
| Neon serverless Postgres | Managed DB | Less control over tuning | Zero ops overhead; free tier suitable |
| Supabase Storage | Managed files | Vendor dependency | Fastest path to reliable file storage |
| No end-to-end tests | Unit + integration | Full browser test coverage | Time constraint; manual verification |

---

## Summary

TalentOS MVP is a well-bounded **Modular Monolith** where:

- Each domain module (auth, company, job, candidate, application, resume, analytics, notification, user) owns its model, repository, service, and controller.
- Cross-module interaction is strictly service-to-service, never repository-to-repository.
- The backend exposes a clean REST API secured by JWT and enforced by Spring Security RBAC.
- The frontend uses Next.js App Router with route groups, protected by middleware, and driven by TanStack Query.
- AI analysis is a first-class feature powered by Google Gemini and stored for efficient retrieval.
- Deployment is zero-ops: Vercel (frontend) + Railway (backend) + Neon (database) + Supabase (storage).

This architecture is intentionally simple enough for one developer to build in 7 days while maintaining clean separation of concerns, extensibility, and professional engineering standards.

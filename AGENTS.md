# SkillHub — AGENTS.md

SkillHub is a self-hosted agent skill registry with a REST backend, React UI,
security scanner, and ClawHub CLI compatibility layer.

## Task-Specific Instructions

Read the relevant skills before working in these areas. Paths below are relative
to `.agents/skills/`.

| Task | Skill |
|------|-------|
| Write or review Java/TypeScript code | `code-conventions/SKILL.md` |
| Add or refactor backend code, choose module boundaries | `backend-module-structure/SKILL.md` |
| Change React components, frontend state, or API calls | `frontend-conventions/SKILL.md` |
| Change REST APIs, auth/RBAC, namespaces, or ClawHub compatibility | `api-and-namespace-design/SKILL.md` |
| Change publishing, review, governance, visibility, or version resolution | `skill-lifecycle/SKILL.md` |
| Start local services, validate changes, or use isolated worktrees | `dev-workflow/SKILL.md` |
| Write tests, smoke tests, E2E tests, or change CI | `testing-and-ci/SKILL.md` |
| Prepare or review a pull request | `pr-submission/SKILL.md` |

## Repository Navigation

| Location | Purpose |
|----------|---------|
| `server/skillhub-app/` | Controllers, DTOs, workflow orchestration, query repositories |
| `server/skillhub-domain/` | Entities, business rules, domain services, repository ports |
| `server/skillhub-infra/` | Domain repository implementations |
| `server/skillhub-auth/` | Authentication, sessions, API tokens, RBAC |
| `server/skillhub-search/` | Search SPI and PostgreSQL implementation |
| `server/skillhub-storage/` | Object storage SPI and local/S3 implementations |
| `server/skillhub-notification/` | Notification delivery and SSE |
| `web/src/` | React frontend organized into app, pages, features, entities, shared |
| `web/e2e/` | Playwright tests |
| `scanner/` | Python/FastAPI security scanner |
| `scripts/` | Development, deployment, and smoke-test scripts |
| `docs/` | Design documents and user guide in `docs/skillhub/` |

Java source lives under each module's `src/main/java/com/iflytek/skillhub/`.
Backend tests live in the corresponding module's `src/test/java/`.
Use `server/pom.xml`, `web/package.json`, and the deployment configuration for
current versions and dependencies. Use `Makefile` for available commands.

## Backend Boundaries

- Controllers handle transport, auth context, request binding, and response wrapping.
- App services orchestrate workflows. Put read-model joins and presentation
  projection in app `*QueryRepository` classes under `com.iflytek.skillhub.repository`.
- Domain services own business rules and state transitions, returning domain objects.
- Define domain repository interfaces in `skillhub-domain` and implement them in
  `skillhub-infra`.
- Keep domain dependencies inward. The existing domain dependency on
  `skillhub-storage` is a known deviation. Avoid further cross-module dependencies
  from domain.
- Explain necessary direct SQL or EntityManager usage in a class-level comment.
- Use `LocalizedDomainException` with i18n keys for user-facing domain errors.
- User identities are always `String`, including API, permission, and audit fields.
- Include `package-info.java` for Java packages.

## Frontend and Layout

- Follow Feature-Sliced Design and place code at the lowest appropriate layer.
- Use generated OpenAPI types for API interactions and strict TypeScript without `any`.
- Use TanStack Query for server state and Zustand for local/UI state.
  Data fetching belongs in query hooks.
- Compose UI with Radix primitives, cva variants, and the existing `cn()` utility
  in `web/src/shared/lib/utils.ts`.
- Avoid unnecessary secondary labels and descriptions. Let the layout do the explaining.

## Generated Files and API Changes

- Regenerate `web/src/api/generated/schema.d.ts` with `make generate-api` and
  include the updated file with API contract changes.
- Regenerate generated DTOs through their owning generator. Check generation
  markers under `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/`
  to identify which files are generated.
- Update documentation when APIs, authentication flows, deployment, or operator
  workflows change.

## Lifecycle and Compatibility

- Read `docs/14-skill-lifecycle.md` when changing lifecycle behavior. It is the
  authoritative state model. Use the independent `hidden` flag for governance
  visibility, and account for the legacy `SkillStatus.HIDDEN` enum value.
- Recalculate `latestVersionId` when yanking a published version.
- Read `docs/07-skill-protocol.md` and `SkillPackagePolicy` when changing package
  validation. Keep `SKILL.md` and existing package conventions compatible.
- Preserve namespace coordinates `@{namespace_slug}/{skill_slug}` and ClawHub
  mappings. The API/namespace skill contains the mapping and conflict rules.
- Preserve replaceable search and storage SPI boundaries and audit trails for
  publishing, downloads, deletion, and authorization.

## Development and Validation

Use `make dev-all` to start the stack and `make dev-status` to inspect it.
When validating Java changes against a running local backend, restart it with
`make dev-server-restart`. Frontend development uses Vite HMR.
See `docs/dev-workflow.md` for setup, local authentication, and troubleshooting.
For parallel worktrees, read `docs/13-parallel-workflow.md`.

Use `make test-backend-app` for backend app tests. It includes dependent modules
with `-am`, avoiding stale Maven artifacts from standalone app-module builds.
Add or update tests for changed behavior and relevant smoke tests for operator
workflows.

Before opening a PR, run the following checks and report any checks that could
not be completed.

```bash
make test-backend-app
make typecheck-web
make lint-web
make staging
```

Use conventional commit and PR titles in the form `<type>(<scope>): <description>`.
The PR submission skill contains the checklist and body requirements.

## Security and Logging

- Store API tokens as prefix-based secure hashes.
- Keep OAuth2 secrets and other credentials out of logs and commits.
- Bootstrap admin is for zero-config quickstart only. Disable it with
  `BOOTSTRAP_ADMIN_ENABLED=false` when appropriate for the deployment.
- Use SLF4J and MDC for backend request tracing, Python structured logging for the
  scanner, and `console.error` / `console.warn` for frontend errors and warnings.

## Design References

Read the relevant document when changing the corresponding design.

| Area | Document |
|------|----------|
| Product direction and scope | `docs/00-product-direction.md` |
| System architecture and dependency rules | `docs/01-system-architecture.md` |
| Domain entities and relationships | `docs/02-domain-model.md` |
| OAuth2, CLI Device Flow, API tokens | `docs/03-authentication-design.md` |
| Search SPI and indexing | `docs/04-search-architecture.md` |
| Business workflows | `docs/05-business-flows.md` |
| REST API contracts | `docs/06-api-design.md` |
| Frontend architecture | `docs/08-frontend-architecture.md` |

Use `CONTRIBUTING.md` for contribution guidelines and `.github/workflows/` for CI
requirements.

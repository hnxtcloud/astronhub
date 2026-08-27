# AstronHub Capability Platform Design

## Status

Accepted on 2026-08-27 after a design-tree interview. The canonical domain language is maintained in [CONTEXT.md](../CONTEXT.md).

## Context

The current product is a mature Skill registry whose domain model, API, CLI compatibility layer, lifecycle, search, and documentation are strongly coupled to SkillHub terminology. The product is expanding into AstronHub（星枢）, a self-hosted registry and discovery platform for three kinds of Agent capability:

- Skills
- Plugins
- MCP servers

This expansion must not refactor the existing Skill domain. Plugin and MCP capabilities are added as sibling domains so that upstream Skill changes remain easy to merge and the existing Skill behavior remains stable.

AstronHub is a registry, catalog, discovery, and governance product. It is not an Agent Runtime, remote plugin installer, credential store, MCP runtime, or MCP Gateway.

## Goals

- Present Skills, Plugins, and MCP servers in one product experience.
- Preserve the existing Skill domain, lifecycle, APIs, search, CLI, and ClawHub compatibility behavior.
- Manage cross-runtime Plugin projects with runtime-specific distributions for Codex, Claude Code, and Pi.
- Provide an MCP Catalog that explains which MCP servers exist and how they can be configured.
- Keep MCP Catalog data suitable for a later MCP Gateway without implementing that gateway now.
- Reuse users, namespaces, authentication, namespace membership, and audit infrastructure.
- Keep Plugin and MCP entities, tables, workflows, search indexes, APIs, and frontend features independent.
- Support self-hosted and intranet deployments without implicit outbound network access.

## Non-Goals

- No change to existing Skill lifecycle or package rules.
- No migration of existing Skill tables into generic capability tables.
- No generic Capability aggregate, superclass, lifecycle, repository, or search document.
- No change to the skillhub CLI or ClawHub compatibility API.
- No server-side installation on a user's workstation.
- No MCP proxying, connection execution, health checking, secret storage, or gateway.
- No automatic creation of Skill or MCP entries from Plugin package contents.
- No Plugin-specific software supply-chain scanner in the first phase.
- No Plugin/MCP ratings, comments, favorites, subscriptions, or cross-namespace promotion in the first phase.
- No repository-wide rename of skillhub technical identifiers.

## Product Naming

The user-facing product name is AstronHub（星枢）.

The first implementation changes only brand strings visible in the Web UI, including the application title, header, navigation, and accessible brand labels. Existing repository names, Maven modules, Java packages, database names, environment variables, images, API paths, npm package names, CLI commands, README content, and historical documentation remain unchanged.

New design documents use AstronHub terminology. Existing Skill-specific documents continue to describe the Skill subsystem without a bulk rewrite.

## Domain Boundaries

AstronHub contains three sibling capability domains:

~~~text
Shared platform
  users | namespaces | authentication | namespace membership | audit
       │
       ├── Skill domain (existing and unchanged)
       ├── Plugin domain (new)
       └── MCP Catalog domain (new)

Read-only composition
  Catalog Search ── aggregates independent search results
  Review Inbox  ── aggregates independent review tasks
~~~

Shared platform services do not own Plugin or MCP lifecycle rules. Unified catalog and review views are application-layer projections, not shared write models.

## Coordinates

Plugin and MCP reuse the existing Namespace model and slug validation. Resource identity remains unique within capability type and namespace.

Typed coordinates are used whenever multiple capability types appear together:

- skill:@team/example
- plugin:@team/example
- mcp:@team/example

Within a type-specific page or endpoint, @team/example remains sufficient. The same namespace and slug may exist once in each capability type. Existing Skill and ClawHub coordinate behavior is unchanged.

## Plugin Domain

### Model

~~~text
Plugin Project
└── Plugin Release
    ├── Plugin Distribution: Codex
    ├── Plugin Distribution: Claude Code
    └── Plugin Distribution: Pi
~~~

Plugin Project is the cross-runtime identity displayed in the Plugins catalog. Plugin Release is its versioned release. Plugin Distribution is the native installable representation for one Agent Runtime.

A Plugin Release may support one or more runtimes. A runtime-specific distribution may contain Skills, MCP configuration, hooks, agents, extensions, prompts, themes, or other components understood by that runtime.

### Distribution Sources

A distribution supports either:

- an immutable archive hosted by AstronHub;
- an npm package pinned to a concrete version;
- a Git source pinned to a tag or commit;
- a download URL pinned by a cryptographic digest.

A published external distribution cannot rely only on a mutable branch, latest tag, or unverified URL. Source metadata and integrity information are retained with the release.

Hosted Plugin artifacts use a Plugin-specific object-storage prefix. They must not reuse Skill storage paths or Skill package services.

### Component References

Runtime adapters may inspect a distribution and show its bundled component inventory. Discovery of SKILL.md or MCP configuration does not create standalone Skill or MCP records.

A publisher may explicitly add a non-owning Component Reference from a distribution to an independently published Skill or MCP Server Entry. Referenced resources retain their own owner, visibility, review workflow, and lifecycle. Archive, yank, or hard delete never cascades across a Component Reference.

### Runtime Adapters

Runtime-specific format knowledge is isolated behind Runtime Adapter ports. The first built-in adapter keys are:

- codex
- claude-code
- pi

The database stores runtime keys as strings rather than a database enum. A Runtime Adapter can:

- validate a native manifest and directory shape;
- extract display metadata and component inventory;
- produce an Installation Recipe;
- emit native marketplace or catalog metadata when supported;
- report static validation evidence.

Runtime adapters do not execute an installed plugin and do not convert static validation into a claim that runtime behavior was tested.

### Installation Boundary

AstronHub returns a hosted artifact, immutable source reference, copyable command, configuration snippet, Installation Recipe, or native marketplace feed. Installation is performed by the user's Agent Runtime or local tooling.

Copying an installation command is recorded as a recipe-copy event, not a successful installation.

### Lifecycle

Plugin Project states:

- ACTIVE
- ARCHIVED

Plugin Release states:

- DRAFT
- PENDING_REVIEW
- PUBLISHED
- REJECTED
- YANKED

Normal publication follows DRAFT to PENDING_REVIEW to PUBLISHED or REJECTED. A published release can be withdrawn only by moving to YANKED. Hidden is an independent governance overlay on the project.

The latest distributable release pointer, if introduced, may point only to PUBLISHED releases and must be recalculated after yank. It is a Plugin-specific rule and must not call or generalize Skill lifecycle code.

### Availability and Compatibility

Distribution availability is an independent overlay:

- AVAILABLE
- SOURCE_UNAVAILABLE
- INTEGRITY_MISMATCH
- UNKNOWN

An availability change does not mutate release lifecycle. If a verified AstronHub-hosted copy remains available, it can still be distributed after the external source disappears. An external-only distribution that cannot be resolved has its installation action disabled until an administrator resolves or yanks it.

Runtime compatibility evidence is expressed separately:

- DECLARED
- MANIFEST_VALIDATED
- TESTED
- VALIDATION_FAILED
- UNKNOWN

Optional runtime version constraints may be stored per distribution. MANIFEST_VALIDATED never implies TESTED.

### Security

Every Plugin Release starts with NOT_SCANNED. The supported evidence vocabulary is:

- NOT_SCANNED
- SCAN_PASSED
- SCAN_WARNINGS
- SCAN_FAILED

Review approval is not security assessment. The first phase has no component capable of producing SCAN_PASSED, and administrators cannot set it manually. A future scanner must attach durable evidence to the exact immutable distribution content.

## MCP Catalog Domain

### Model

~~~text
MCP Server Entry
└── MCP Entry Revision
    ├── Source Snapshot
    ├── Local Curation
    └── Connection Profile(s)
~~~

MCP Server Entry is the stable catalog identity. MCP Entry Revision is an immutable revision of published metadata. Connection Profile is a secret-free configuration template.

MCP Server Entry states:

- ACTIVE
- ARCHIVED

MCP Entry Revision states:

- DRAFT
- PENDING_REVIEW
- PUBLISHED
- REJECTED

Published revisions remain immutable. Editing a published entry creates a new draft revision while the previously published revision remains visible until the new revision is approved. Hidden is an independent governance overlay on the entry.

### Transport Vocabulary

Connection Profiles support:

- STDIO
- STREAMABLE_HTTP
- LEGACY_HTTP_SSE
- CUSTOM

LEGACY_HTTP_SSE is displayed as a compatibility option, not a recommended modern transport. CUSTOM profiles must clearly describe their non-standard transport.

The protocol baseline should be pinned during implementation to the then-current official MCP specification. The design baseline is the 2026-07-28 transport model.

### Configuration Variables

A Connection Profile stores only a template and structured variable definitions. A variable definition includes:

- name;
- description;
- required flag;
- secret flag;
- optional non-secret default;
- optional validation hint.

Secret variables use placeholders in templates. Secret values are never accepted, persisted, logged, indexed, or returned by the MCP Catalog.

A future MCP Gateway will bind secret variables to Credential References stored outside the catalog.

### Catalog Sources

The first phase accepts:

- manual Namespace submissions;
- imported standard server.json metadata;
- an optional Official MCP Registry source adapter.

There is no web crawler.

All background external synchronization is disabled by default. Intranet deployments can use only manual entry and file import. A network-connected operator may explicitly enable the Official MCP Registry adapter and its schedule.

Explicit publication, import, or revalidation actions may access the source selected by the operator. They do not imply a default background egress policy.

### Source Reconciliation

Imported data is separated into:

- Source Snapshot: immutable upstream data and provenance;
- Local Curation: local category, tags, visibility, review outcome, display additions, and governance metadata.

An upstream change creates a new Source Snapshot and draft MCP Entry Revision. It does not overwrite Local Curation or automatically publish.

If an upstream record disappears, the local entry is marked SOURCE_UNAVAILABLE rather than deleted. Potential duplicates are reported for administrator review and are never automatically merged.

### Official Registry Compatibility

AstronHub exposes two MCP API surfaces:

- Native API for authenticated publication, governance, private discovery, and the future Gateway.
- A separate read-only compatibility adapter that serializes public catalog data in the Official MCP Registry format.

The compatibility adapter returns only entries that are PUBLIC, have a published revision, and are neither hidden nor archived. NAMESPACE_ONLY and PRIVATE entries are available only through the authenticated Native API.

The compatibility adapter does not implement the Official Registry's publisher authentication or write protocol in the first phase. Implementation must pin and contract-test against a specific upstream OpenAPI version so preview changes do not leak into the MCP domain.

### Future Gateway Boundary

The MCP Gateway is a future sibling capability, not part of MCP Catalog.

~~~text
MCP Server Entry
└── published MCP Entry Revision
    └── Connection Profile
        └── MCP Connection
            ├── environment parameters
            ├── Credential Reference
            └── runtime and health state
~~~

An MCP Connection pins one published Entry Revision and profile. Publishing a new revision never silently changes an existing connection. Connection upgrade is an explicit Gateway action.

The current phase reserves stable entry, revision, and profile identifiers but creates no connection, credential, proxy, health, or execution tables.

## Visibility and Authorization

Plugin and MCP independently support:

- PUBLIC: anonymously discoverable after publication;
- NAMESPACE_ONLY: visible to Namespace members;
- PRIVATE: visible to the owner and Namespace administrators.

Visibility does not bypass review. Plugin and MCP do not reuse the Skill visibility enum or its workflow branches.

Namespace roles retain their current meanings:

- MEMBER may create drafts and submit them for review.
- ADMIN and OWNER may review team-space submissions and manage resources in their Namespace.

Platform roles are domain-specific:

- SKILL_ADMIN continues to govern only Skills.
- PLUGIN_ADMIN governs global Plugin submissions and Plugin platform actions.
- MCP_ADMIN governs global MCP submissions and MCP platform actions.
- SUPER_ADMIN governs all domains and may publish directly.

No CAPABILITY_ADMIN role is introduced.

Plugin and MCP use separate review task entities and services. A unified review inbox may join their read models, but it cannot transition state directly without dispatching to the owning domain service.

## Delete, Archive, Hide, and Yank

- Owners and Namespace administrators may delete DRAFT and REJECTED releases or revisions.
- Published Plugin Releases cannot be deleted through normal workflows; they may be YANKED.
- Published MCP Entry Revisions are immutable and cannot be normally deleted.
- Plugin Projects and MCP Server Entries are retired through ARCHIVED.
- Platform moderation uses a hidden overlay.
- Cross-namespace promotion is not implemented in the first phase.

SUPER_ADMIN may hard-delete malicious content or satisfy compliance and legal requirements. Hard delete removes artifacts, templates, and non-essential display data while retaining a minimal Audit Tombstone. Hard-delete storage cleanup must follow the project's existing post-commit compensation pattern rather than tying database transactions to object-storage availability.

## Search and Discovery

Search ownership remains independent:

~~~text
Existing Skill search ─┐
Plugin search ─────────┼── Catalog Search aggregation ── unified result page
MCP search ────────────┘
~~~

The existing Skill search implementation and index remain unchanged. Plugin and MCP have separate search documents or domain-specific PostgreSQL full-text queries. The application-layer Catalog Search merges results and supports All, Skills, Plugins, and MCP filters.

Unified results contain a type discriminator and typed coordinate. They do not require a generic Capability table.

## Metrics and Social Features

The first phase does not reuse or generalize Skill stars, ratings, subscriptions, or comments.

Plugin may independently record:

- detail views;
- hosted distribution downloads;
- Installation Recipe copies.

MCP may independently record:

- detail views;
- Connection Profile copies.

Recipe or profile copies must not be labeled as successful installation or connection. Future unified favorites, if required, are implemented as a new aggregation feature without migrating existing Skill social tables.

## Native API Shape

Recommended resource families:

~~~text
/api/v1/plugins
/api/v1/plugins/{namespace}/{slug}
/api/v1/plugins/{namespace}/{slug}/releases
/api/v1/plugins/{namespace}/{slug}/releases/{version}/distributions

/api/v1/mcp-servers
/api/v1/mcp-servers/{namespace}/{slug}
/api/v1/mcp-servers/{namespace}/{slug}/revisions
/api/v1/mcp-servers/{namespace}/{slug}/revisions/{revision}
/api/v1/mcp-servers/{namespace}/{slug}/revisions/{revision}/profiles

/api/v1/catalog/search
~~~

Controllers remain transport-only. Native APIs use the existing response envelope, String user identities, CSRF/session behavior, and OpenAPI generation process.

Exact write, review, governance, import, and compatibility routes are defined in implementation tickets while preserving these resource names and domain boundaries.

## Persistence Model

Recommended new table families:

Plugin:

- plugin_project
- plugin_release
- plugin_distribution
- plugin_component_reference
- plugin_review_task
- plugin_search_document
- Plugin-specific availability, compatibility evidence, metrics, and audit records as required

MCP:

- mcp_server_entry
- mcp_entry_revision
- mcp_connection_profile
- mcp_source_snapshot
- mcp_local_curation
- mcp_review_task
- mcp_search_document
- MCP-specific availability, metrics, and audit records as required

Every aggregate references the existing namespace and uses String user identity fields. No table is renamed and no existing Skill table receives a capability-type discriminator.

Migration scripts are additive. Foreign keys and indexes must be named by the owning domain. MCP source payloads and Plugin manifest data may be stored as validated JSON where preserving upstream data is necessary, while frequently queried governance and discovery fields remain typed columns.

## Backend Placement

Do not add Maven modules in the first phase. Use new, cohesive packages inside the existing seven-module architecture:

- skillhub-domain
  - com.iflytek.skillhub.domain.plugin
  - com.iflytek.skillhub.domain.mcp
- skillhub-infra
  - JPA implementations for Plugin and MCP repository ports
- skillhub-app
  - portal/admin controllers
  - Plugin and MCP app services
  - Catalog Search and unified review query repositories
  - import and compatibility adapters
- skillhub-search
  - independent Plugin and MCP search implementations
- skillhub-storage
  - existing storage SPI only; Plugin-specific keys are orchestrated outside the domain

Runtime Adapter ports belong to the Plugin domain. Implementations that require filesystem, archive, source-fetching, or external protocol libraries belong outside the domain and are assembled by skillhub-app.

MCP Registry source and compatibility adapters must not be imported by the MCP domain. They translate between pinned external contracts and domain commands/read models.

## Frontend Placement

Use Feature-Sliced Design:

- web/src/entities/plugin
- web/src/entities/mcp-server
- web/src/features/plugin
- web/src/features/mcp-catalog
- web/src/features/catalog-search
- web/src/features/capability-review for read-only inbox composition
- web/src/pages for route-level catalog, detail, publish, and review pages

TanStack Query owns server state. API clients use generated OpenAPI types. User-visible text is translated through the existing i18n system.

The shared layer may contain generic presentation primitives such as typed-coordinate badges. It must not contain Plugin or MCP lifecycle rules.

## Merge-Conflict Strategy

- Prefer additive files and packages.
- Do not rename or move existing Skill classes.
- Do not add a capability discriminator to existing Skill tables or DTOs.
- Do not generalize existing Skill services, review tasks, search documents, social tables, or controllers.
- Keep shared-file edits limited to navigation, routing, role registration, security policies, dependency assembly, migrations, and generated OpenAPI output.
- Keep brand edits limited to visible Web UI strings in the first phase.
- Split implementation into tracer bullets so shared integration files are touched late and deliberately.

## Delivery Slices

Recommended order:

1. Foundation
   - additive roles, typed-coordinate presentation, domain packages, migrations, repository ports
2. Plugin catalog read path
   - project/release/distribution reads, Codex adapter, visible UI
3. Plugin write and governance path
   - publish, review, archive, yank, hosted/external distributions
4. Additional Runtime Adapters
   - Claude Code and Pi
5. MCP manual catalog
   - entry/revision/profile publication, review, visibility, UI
6. MCP import and compatibility
   - server.json import, optional Registry Source adapter, read-only compatibility API
7. Unified discovery
   - independent indexes, Catalog Search aggregation, unified result page and review inbox
8. Visible brand switch
   - AstronHub strings, titles, navigation, and accessibility labels

Each slice must leave the existing Skill tests passing and must avoid partially exposing write actions before their permission and audit paths exist.

## Testing Strategy

Backend:

- unit-test Plugin and MCP state transitions through public domain services;
- verify domain-specific authorization and SUPER_ADMIN bypass;
- test visibility for anonymous, owner, Namespace member, Namespace administrator, and platform administrator;
- test immutable published releases/revisions and delete/yank/archive behavior;
- contract-test each Runtime Adapter with representative native packages;
- test immutable source pinning, digest mismatch, and unavailable sources;
- test manual import and disabled-by-default synchronization;
- contract-test the MCP compatibility adapter against a pinned upstream OpenAPI specification;
- verify Catalog Search never returns private or hidden results to unauthorized callers;
- run existing Skill lifecycle, compatibility, search, and social regression tests unchanged.

Frontend:

- test type filters and typed coordinates in unified search;
- test Plugin runtime selection and Installation Recipe rendering;
- test MCP profile rendering without secret values;
- test role- and visibility-aware actions;
- test independent review flows and the composed review inbox;
- test visible AstronHub branding without changing technical command examples.

Required verification for implementation work:

~~~text
make test-backend-app
make typecheck-web
make lint-web
make test-frontend
make generate-api
make staging
~~~

## Deferred Work

- MCP Gateway, MCP Connection lifecycle, Credential Store, proxying, health, and observability
- explicit MCP Connection upgrade workflow
- Plugin software supply-chain scanner and signed assessment evidence
- Plugin/MCP promotion workflows
- Plugin/MCP social features and unified favorites
- local CLI installation support
- additional Agent Runtime adapters
- repository-wide technical rebranding

These items require separate design decisions and must not be inferred from the extension points reserved by this specification.

## Related Decisions

- [Keep capability domains independent](./adr/0001-keep-capability-domains-independent.md)
- [Separate MCP Catalog from MCP Gateway](./adr/0002-separate-mcp-catalog-from-gateway.md)
- [Model cross-runtime Plugin distributions](./adr/0003-model-cross-runtime-plugin-distributions.md)
- [Disable external synchronization by default](./adr/0004-disable-external-sync-by-default.md)

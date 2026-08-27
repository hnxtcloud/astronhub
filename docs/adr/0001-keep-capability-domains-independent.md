# Keep capability domains independent

AstronHub adds Plugin and MCP Catalog as sibling domains beside the existing Skill domain. They may reuse users, namespaces, authentication, namespace membership, and audit infrastructure, but they own separate entities, tables, lifecycles, review tasks, APIs, search indexes, and frontend features. Unified search and review are read-only application projections; there is no generic Capability aggregate or migration of existing Skill code. This preserves upstream mergeability and prevents the mature Skill lifecycle from becoming an accidental lowest-common-denominator abstraction.

## Consequences

- Existing Skill behavior and technical identifiers remain unchanged.
- Similar concepts may have intentionally separate implementations in each domain.
- Cross-domain features compose read models or dispatch to the owning domain instead of mutating shared state.

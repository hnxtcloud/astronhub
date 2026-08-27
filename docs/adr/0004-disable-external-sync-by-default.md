# Disable external synchronization by default

AstronHub performs no background external registry synchronization unless an operator explicitly enables a Registry Source and schedule. Manual MCP submission and file import remain fully usable in intranet and air-gapped deployments, while network-connected deployments may opt into an isolated Official MCP Registry adapter. This chooses predictable network egress and self-hosted operability over automatic catalog freshness.

## Consequences

- A fresh deployment makes no background requests to the Official MCP Registry.
- Imported source data records provenance and remains separate from local curation.
- Upstream disappearance marks availability but does not automatically delete or unpublish local records.

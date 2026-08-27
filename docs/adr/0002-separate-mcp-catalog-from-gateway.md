# Separate MCP Catalog from MCP Gateway

The first MCP capability is a metadata catalog, not a runtime gateway. MCP Server Entries, immutable Entry Revisions, and secret-free Connection Profiles support discovery today and provide stable inputs for a future Gateway. The future Gateway will create separate MCP Connections that pin a published revision and bind environment parameters and Credential References; publishing a catalog revision will never silently reconfigure a running connection. This separation keeps credentials and runtime state out of a broadly discoverable catalog while preserving a deliberate integration seam.

## Consequences

- The current phase contains no credentials, live connections, proxying, deployment, health, or runtime status.
- Published MCP Entry Revisions are immutable.
- Gateway development requires a separate design and persistence model.

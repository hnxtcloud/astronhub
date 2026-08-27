# Model cross-runtime Plugin distributions

AstronHub models a Plugin Project as the stable cross-runtime identity, a Plugin Release as its version, and Plugin Distributions as runtime-native installable representations. Codex, Claude Code, Pi, and future runtimes are isolated behind Runtime Adapters that validate native formats and produce Installation Recipes. AstronHub does not assume that these runtimes share one plugin package format, and the server does not install plugins on user devices.

## Consequences

- A release can contain multiple independently validated runtime distributions.
- Runtime keys are extensible strings rather than a database enum.
- Bundled Skills and MCP configurations are inventory only unless explicitly linked to independent catalog entries.
- Static manifest validation does not imply runtime testing or security approval.

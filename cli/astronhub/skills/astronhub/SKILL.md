---
name: astronhub
description: Use AstronHub CLI to install project Node dependencies or find, install, update and remove Agent Skills from the organization's private registries.
---

# AstronHub CLI

1. Run commands from the target project or workspace package directory.
2. Run `astronhub --version` and read `astronhub config --json`.
   If unavailable, ask the operator for the approved CLI package/location and registry setup.
3. Read `astronhub install --help` for Node dependencies, or
   `astronhub skill --help` for Agent Skills. Use the installed version's help as the command reference.
4. Run the requested operation with explicit targets and `--non-interactive --json`.
5. Check the exit code and result. For dependencies, review package.json and the lockfile.
   For Skills, verify the installed files and reported target. Report partial failures.

## Node dependencies

`astronhub install axios --non-interactive --json`

`astronhub install @types/node@26.6.5 -D --non-interactive --json`

`astronhub install` installs the dependencies already declared by the project.
The CLI detects npm/pnpm from project declarations and lockfiles; use `--pm` only when needed.
Use `--dry-run --json` to preview the command without network access or mutations.
Keep dependency installation through AstronHub so scoped metadata requests pass through its gateway.
The CLI respects the package manager's normal lifecycle-script policy; use `--ignore-scripts` when requested.

## Agent Skills

`astronhub skill find review --non-interactive --json`

`astronhub skill add @global/code-review -a codex --non-interactive --json`

`astronhub skill update @global/code-review --check --non-interactive --json`

`astronhub skill update @global/code-review --non-interactive --json`

Default scope is the current project; add `-g` only for an explicitly requested user-level installation.
Updates require explicit coordinates. Use `--force` only when replacing local changes is authorized.
An installed Skill may require the Agent to refresh discovery or start a new session before it is available.

## Configuration

`npmRegistry` is the private npm registry; `skillRegistry` is the SkillHub server URL.
Credentials are provided through `ASTRONHUB_NPM_TOKEN` (npm gateway) and `ASTRONHUB_TOKEN` (SkillHub).
SkillHub login credentials and installation inventory share the existing `~/.skillhub` state.
Keep tokens in the operator's environment or user credential store.

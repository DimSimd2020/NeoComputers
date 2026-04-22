# Multi-Agent Orchestration

This repository is structured for contract-first work across isolated agent domains.

## Ownership Zones

- `panama-bridge/` is the only shared surface.
- `neocore-native/` belongs to native C++ work.
- `neocore-forge/` belongs to Java / NeoForge work.

## Branch Model

- `main` contains only buildable, integrated code.
- `agent/5.3-cpp-core` is for native implementation work.
- `agent/5.4-forge-ui` is for Forge-side implementation work.

## Contract-First Rule

1. Define or update the cross-language contract in `panama-bridge/`.
2. Freeze that contract before parallel work starts.
3. Split follow-up work by ownership zone.
4. Do not let one agent resolve merge conflicts created in another agent's domain.

## Context Diet

### GPT-5.3 Codex

Provide only:

- `PROJECT.md`
- `ORCHESTRATION.md`
- `panama-bridge/include/`
- a narrow native task description

### GPT-5.4

Provide only:

- `PROJECT.md`
- `ORCHESTRATION.md`
- `panama-bridge/java/`
- NeoForge-side documentation
- a narrow Forge task description

## Merge Discipline

- Keep `main` free of experimental half-integrations.
- Merge only contract-compatible diffs.
- Review the final diff manually at the seams between zones.


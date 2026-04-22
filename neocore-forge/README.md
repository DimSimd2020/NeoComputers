# neocore-forge

Java and NeoForge integration layer.

## Ownership

This directory is reserved for:

- blocks, items, menus, screens
- NeoForge event handling
- networking and persistence glue
- UI and gameplay integration

## Boundary Rules

- Do not embed native emulator internals here.
- Depend on `panama-bridge/` only through the published contract surface.
- Keep gameplay code decoupled from low-level emulation details.


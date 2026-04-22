# neocore-native

Native C++ emulator core.

## Ownership

This directory is reserved for:

- CPU / device emulation
- memory model implementation
- serialization of native state
- performance-critical backend code

## Boundary Rules

- Do not add Java or NeoForge code here.
- Depend on `panama-bridge/` only for shared ABI/API contracts.
- Keep public native entry points narrow and explicit.


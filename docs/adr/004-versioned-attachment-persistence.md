# ADR-004: Versioned player attachment for assignment persistence

Status: accepted

## Context

Per-player bar assignments need persistence that survives relog, is
server-authoritative, migration-friendly and poison-resistant. Alternatives
considered: SavedData keyed by UUID, sidecar files via `PlayerEvent`, plain
Codec attachment, `SynchedEntityData`. No org repo has prior art.

## Decision

Store assignments in a **serialized NeoForge player attachment** using a
custom `IAttachmentSerializer<CompoundTag, T>` (ISS `PlayerMagicProvider`
shape) with an embedded `schemaVersion` int and a forward-only upgrade ladder;
`copyOnDeath()` keeps assignments on death; unknown ids are preserved raw as
inert slots; a hotbar-index field is reserved from day one so the stretch
multi-hotbar goal needs no breaking format change.

## Consequences

- Relog persistence comes free with the player's `.dat`; no orphan files, no
  UUID-keyed garbage collection.
- No DataFixerUpper exists for modded attachment data - codec errors fail
  closed to defaults, so the custom serializer must log-and-preserve instead
  (diagnostics issue in hotbar #25).
- Attachment registry ids must never be renamed (renames orphan data).

Links: hotbar issues #24, #25; spike findings on hotbar #5; sync interplay in
ADR-003.

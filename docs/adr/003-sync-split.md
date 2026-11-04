# ADR-003: Sync split - registry sync, explicit snapshots, native projection

Status: accepted

## Context

Ability state crosses the wire in three very different shapes: static
definitions, per-player persistent assignments, and volatile availability
(cooldowns, mana). NeoForge 1.21.1 offers registry id-mapping sync, play-phase
payloads, synced attachments (`sync()` since 21.1.197) and entity data - with
verified caveats (own-attachment initial login sync unverified at 21.1.259;
`optional()` channels tolerate absence but never version skew).

## Decision

- **Definitions**: registry sync (ADR-001) reconciles the *entry set and
  ids* only - never content (`RegistrySnapshot` carries ids + aliases).
  Content needs no payload for code-registered abilities: both sides build
  the same catalog at mod init, so entry sets match by construction (the
  shipped menu reads its definitions client-side with no catalog payload);
  datapack-sourced overlay content (core#12's deferred data-map sub-part)
  will need explicit transport when it lands. (Precision amendment recorded
  with core#19's closure, 2026-11-04.)
- **Persistent player state**: serialized player attachment as source of truth
  (ADR-004) plus **explicit full snapshots** on login/respawn/dimension change
  and deltas on mutation - the ISS-proven pattern; the explicit login snapshot
  is mandatory regardless of attachment sync. (Shipped form: every mutation is
  answered by a full-snapshot echo instead of a delta - ADR-011's flow; state
  is a few hundred bytes, so deltas stay deferred. See `docs/sync.md`.)
- **Volatile availability**: client projection of natively-synced provider
  state (ISS already syncs mana/cooldowns/learned); core re-syncs only what
  providers do not.
- One common registrar: `event.registrar(modid).versioned(N).optional()`
  (core's channel is at N=2 since the target-hint field); codec-breaking
  changes bump N and ship both sides together.
- Client->server payloads are requests only; rejection re-sends the snapshot.

## Consequences

- Client caches are never trusted; reconnect/respawn correctness is
  snapshot-driven by design.
- Payload inventory is small (snapshot, assign, select, switch, activate -
  deltas deferred per the amendment above; current table in `docs/sync.md`).
- Version-skew mistakes disconnect - the version bump discipline is mandatory,
  documented in `docs/sync.md` (closed via core issue #19).

Links: core issue #19; spike findings on core #4 (full evidence); hotbar
issues #24, #25.

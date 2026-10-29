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

- **Definitions**: registry sync (ADR-001) - no custom packet.
- **Persistent player state**: serialized player attachment as source of truth
  (ADR-004) plus **explicit full snapshots** on login/respawn/dimension change
  and deltas on mutation - the ISS-proven pattern; the explicit login snapshot
  is mandatory regardless of attachment sync.
- **Volatile availability**: client projection of natively-synced provider
  state (ISS already syncs mana/cooldowns/learned); core re-syncs only what
  providers do not.
- One common registrar: `event.registrar(modid).versioned("1").optional()`;
  codec-breaking changes bump the version and ship both sides together.
- Client->server payloads are requests only; rejection re-sends the snapshot.

## Consequences

- Client caches are never trusted; reconnect/respawn correctness is
  snapshot-driven by design.
- Payload inventory is small (snapshot, delta, assign, activate).
- Version-skew mistakes disconnect - the version bump discipline is mandatory,
  documented in core issue #19.

Links: core issue #19; spike findings on core #4 (full evidence); hotbar
issues #24, #25.

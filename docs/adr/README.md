# Architecture decision records

Short, single-decision records for `tensura-ability-core` and the ecosystem it
anchors. Format: Context / Decision / Consequences / Links. Status values:
`proposed`, `accepted`, `superseded`. New decisions get an ADR in the same PR
that records them in the originating issue (traceability rule, issue #28).

| ADR | Decision | Status |
|---|---|---|
| [001](001-registry-based-discovery.md) | Registry-based ability discovery (built-in registry + data-map overlay) | accepted |
| [002](002-chargingmodel-exclusivity.md) | chargingModel exclusivity - one charging site per ability | accepted |
| [003](003-sync-split.md) | Sync split: registry sync, explicit snapshots, native projection | accepted |
| [004](004-versioned-attachment-persistence.md) | Versioned player attachment for assignment persistence | accepted |
| [005](005-arr-interop-stance.md) | All Rights Reserved upstream: interop-only posture | accepted |
| [006](006-private-datapack-namespace.md) | Private datapack namespace ownership | accepted |
| [007](007-spellbook-hotbar-location.md) | Spellbook hotbar lives in the hotbar repo's ISS adapter | accepted |
| [008](008-no-battlewill-bc-repo.md) | No dedicated battlewill Better Combat repository | accepted |
| [009](009-conceptual-is-spiritual.md) | Brief's "conceptual magic" maps to the game's SPIRITUAL category | accepted |
| [010](010-native-skill-registration-for-additive-battlewills.md) | Additive battlewills register as native ManasCore skills | accepted |

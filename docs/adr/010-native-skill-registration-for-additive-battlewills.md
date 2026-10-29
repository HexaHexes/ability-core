# ADR-010: Additive battlewills register as native ManasCore skills

Status: accepted

## Context

Native battlewills are first-class `ManasSkill`s (23 registered arts, aura
currency, `EnergyHelper` check-and-pay inside ability callbacks, activation via
`startHoldSkill` with no separate keybind). Additive content (unarmed set
first) had three options: (a) native skill registration through ManasCore's
registry subclassing Tensura's `Battlewill`; (b) core-registry abilities with
an EXTERNAL aura charge and no ManasCore linkage; (c) integration-only, no new
content (DEC-06).

## Decision

**Option (a), maintainer ruling.** Additive arts subclass `Battlewill` and
register through `SkillAPI.getSkillRegistry()` - the official addon-example
pattern. Costs live in our own config sections (matching the native
config-not-datapack cost model); datapack rows carry metadata/style/restrictions
only, with `execution_adapter` ids pointing at the code. Obligations: accept
GPLv3 ManasCore linkage for `tensura-battlewills` and record attribution in the
mandatory NOTICE plus a linkage note (battlewills issue #13); Tensura ARR usage
stays structural/API-use only (ADR-005).

## Consequences

- Additive arts get native activation, aura payment, preset gating and
  hotbar/combo interop with zero custom adapters.
- Rejected alternatives recorded: (b) reimplements activation for weaker
  parity; (c) leaves the brief's unarmed/style gaps unfilled.
- The battlewill repo's licence posture now includes a GPLv3-linked
  dependency - surfaced in NOTICE and README before the first such release.

Links: bw issue #11 (ruling comment), bw issues #13, #14, #16-#18; inventory
DEC-06 row.

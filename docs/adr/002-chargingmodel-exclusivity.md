# ADR-002: chargingModel exclusivity - one charging site per ability

Status: accepted

## Context

The brief forbids duplicate resource consumption when native and bridge paths
could both charge. Verified deduction points differ per provider: ISS charges
in exactly one place (`AbstractSpell.castSpell` at cast execution); Tensura
charges inside each ability's own callbacks via `EnergyHelper` check-and-pay -
there is no pipeline-level charge and no post-activation event.

## Decision

Every ability declares exactly one `chargingModel` at registration:
`NATIVE` (provider's pipeline charges; core must not) or `EXTERNAL` (core
charges after successful activation). Registration **rejects** misdeclared or
dual-chargeable abilities.

Bridge payment modes map onto the model exactly as follows (REQ-BR-06,
SPIKE-br-2 §2) - the mapping is validation input, not a second charging site:

| Bridge mode | For a `NATIVE` ability | For an `EXTERNAL` ability |
|---|---|---|
| `native` (default) | pass-through: the provider's single site charges | pass-through to core's single post-success site |
| `scaled`, `fixed` | only the sanctioned hooks, as **modifications of the single site** (ISS `SpellOnCastEvent.setManaCost` downward; definition-time Tensura cost) - never an added site | core charges once post-success (the model's own site) |
| `free`, `disabled` | **only with an explicit per-ability whitelist proving the native path charges zero - never assumed** | same whitelist precondition |

A mode that would create a second charge site for a `NATIVE` ability is
rejected loudly at registration/config load, never at combat time (shared
validation with core#15).

## Consequences

- Double-charging becomes a structural impossibility rather than a runtime
  heuristic; violations fail at registration with loud errors.
- `free`/`disabled` carry an explicit precondition (a per-ability whitelist
  proving the native path charges zero) instead of an assumption; `scaled`/
  `fixed` carry the single-site-only precondition (see the mapping table).
- The multi-ability atomic all-or-nothing case stays deferred (no reservation
  primitive exists) - see bridge issue #14.

Links: core issues #15 (contract), #13 (pipeline order); bridge issues #11,
#12, #13; spike findings on bridge #2 and core #2.

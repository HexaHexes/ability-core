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
dual-chargeable abilities. Bridge payment modes map onto the model and may only
use sanctioned single-site hooks (ISS `SpellOnCastEvent` downward only;
definition-time Tensura costs).

## Consequences

- Double-charging becomes a structural impossibility rather than a runtime
  heuristic; violations fail at registration with loud errors.
- `free`/`fixed` modes carry explicit preconditions instead of assumptions.
- The multi-ability atomic all-or-nothing case stays deferred (no reservation
  primitive exists) - see bridge issue #14.

Links: core issues #15 (contract), #13 (pipeline order); bridge issues #11,
#12, #13; spike findings on bridge #2 and core #2.

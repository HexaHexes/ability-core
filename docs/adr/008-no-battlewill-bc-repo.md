# ADR-008: No dedicated battlewill Better Combat repository

Status: accepted

## Context

The brief proposed a conditional `tensura-battlewills-better-combat` adapter
repo, to be created only if a dedicated adapter is demonstrably cleaner than
putting battlewill integration in the shared bridge (DEC-03).

## Decision

**Do not create the repository.** Better Combat's usable surface is two public
interfaces, one public static getter and JSON data files (only one NeoForge
event exists and it is client-side), so battlewill *style detection* is
read-only attribute lookup that belongs in `tensura-battlewills` behind an
optional gate, while combo *activation* of battlewill payloads is bridge-domain
work already issued there. A third repo would either duplicate the bridge's
combo-event wiring or add an artifact both repos consume for zero behavioural
gain - and the brief explicitly rejects organisational neatness as
justification.

## Consequences

- Five repos remain the full inventory (F is closed as a decision, not
  deferred).
- Battlewill combo support evolves inside existing bridge issues; style logic
  inside battlewills issues.

Links: bw issue #4 (decision comment), bridge issues #7-#10, bw issue #10.

# ADR-009: The brief's "conceptual magic" maps to the game's SPIRITUAL category

Status: accepted

## Context

The affinity model in the project brief names aspectual / **conceptual** /
summoning magic. Tensura 2.0.1.3 ships ASPECTUAL / **SPIRITUAL** / SUMMONING /
MISC - zero matches for "conceptual" anywhere in the jar or lang files
(DEC-05, raised on the mapping spike).

## Decision

Maintainer ruling (Option A): interpret the brief's "conceptual magic" as the
game's **SPIRITUAL** category. Documentation uses the game's terminology with a
note explaining the brief's wording. The hard rules are unchanged: aspectual
and spiritual magics receive only **explicitly assigned** elements; summoning
magic receives none and never inherits its summoned entity's element; unknown
mappings grant nothing (fail closed).

## Consequences

- The mapping table (iss issue #19) targets aspectual/spiritual/summoning tags
  exactly as shipped (`spiritual_magic.json` 39 ids etc.).
- If a future Tensura version introduces a distinct conceptual category, this
  ADR is superseded and the mapping re-opened.

Links: iss issue #5 findings + DEC-05 comment, iss issue #19.

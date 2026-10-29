# ADR-007: Spellbook hotbar lives in the hotbar repo's ISS adapter

Status: accepted

## Context

The equipped-spellbook hotbar could be implemented in `tensura-iss-integration`
(since casting-policy interplay lives there) or entirely inside
`tensura-ability-hotbar` behind an isolated ISS adapter (DEC-02).

## Decision

Implement entirely in `tensura-ability-hotbar` behind an isolated ISS adapter.
Every required entry point is ISS *public API* (`Utils.getPlayerSpellbookStack`,
`SpellSelectionManager.SpellSelectionEvent`, `serverSideInitiateCast`/
`serverSideInitiateQuickCast`, `AbstractSpell.attemptInitiateCast`,
`ClientMagicData` reads) plus Curios sync - none of it involves Tensura, which
is what `tensura-iss-integration` exists for.

## Consequences

- No hotbar -> iss-integration dependency for a UI feature; both repos keep
  clean ownership boundaries.
- Cross-repo policy interplay (casting policies gating spellbook-bar casts)
  happens at event level (repo-agnostic), so no coupling is introduced.
- If future work needs ISS-internal surfaces, re-open this ADR before moving
  code across repos.

Links: hotbar issue #6 findings (section 7), hotbar issues #10-#15.

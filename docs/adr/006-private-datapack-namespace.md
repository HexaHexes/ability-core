# ADR-006: Private datapack namespace ownership

Status: accepted

## Context

Datapack content in this pack lives under silent last-pack-wins semantics for
non-tag files. The overlap audit (official `tensura_iron_spells` and
`tensura-iss-growth`) established hard ownership: `data/irons_spellbooks/**`
gear-existence, `data/gearevolution/addon_chains/irons_spellbooks/**`, the
official's entity-existence and its 22 `data/tensura/tags/**` files are all
owned elsewhere; sanctioned overlap requires byte-identical copies plus README
drift tables.

## Decision

All datapack content from `tensura-iss-integration` (and the ecosystem's new
mods generally) ships under a **private namespace** (`data/tensura_iss_
integration/**` and equivalents), with `ordering = "AFTER"` declared on
`tensura`, `irons_spellbooks`, `tensura_iron_spells` and `tensura-iss-growth`.
Tags from other namespaces are extended only via new tag file names, never
same-path `replace:true`. Event-priority composition: never occupy HIGHEST or
LOWEST on events the official compat already uses.

## Consequences

- No three-way silent overwrite races; ownership is structurally visible.
- Overrides of another mod's paths require an explicit decision issue with
  byte-identical copies or documented drift - never a quiet edit.

Links: iss issue #4 findings (ownership matrix), iss issues #8/#19/#20.

# ADR-012: Hotbar registration, assignability and switching

Status: accepted

## Context

ADR-011 fixed the multi-hotbar state model but assumed every cyclable bar was an
ability bar owned by this mod. The maintainer then required: mods register their
own hotbars (ISS spellbook, Pseudocore curios bar) and declare at registration
whether abilities can be assigned to them; assignment screens must show every
assignable hotbar and hide non-assignable ones; and all registered hotbars join
the Alt+wheel / Alt+1..0 rotation, shown only while active. ADR-011's "layers,
not cycle" clause (DERIVED bars never enter the cycle) contradicts the last
point and needs superseding. Additionally, a client option skips empty bars
during cycling, and assignment screens are exempt from that skipping.

## Decision

1. **Registration API** (hotbar#77): a code API called at mod setup registers a
   hotbar by stable `ResourceLocation` id with a display-name translation key
   and an `assignable` flag. Duplicate ids and the reserved `ability_hotbar:`
   namespace fail loudly at registration. Built-in bars keep positional ids
   (`ability_hotbar:bar_N`) and are implicitly assignable; they are not
   registered. **This supersedes ADR-011's "layers, not cycle" clause**: all
   registered bars - assignable or not - join the switching rotation and render
   only while active. ADR-011's state model (per-bar slots and selections,
   server-owned active bar, inert preservation, optimistic switch) is unchanged.
2. **Assignability gates two surfaces**: the assignment screen lists only
   assignable hotbars and never hides an empty one (skip-empty is a cycling
   rule only), and the server rejects assignment requests naming a bar that
   does not exist or is not assignable. The client-side filter is advisory;
   the server re-validates every mutation.
3. **State placement - schema v3**: the attachment stores only bars whose state
   this mod owns - built-ins plus registered assignable bars - each with a
   persisted `bar_id`, active bar stored **by id** (not index), so ids survive
   re-registration order and index shifts. Non-assignable registrations carry
   no attachment state **of their own**; their content stays with the
   registering mod behind a content-provider API (shipped as hotbar#83/#84).
   **Amended by hotbar#89 (2026-11-04):** a content-owned bar may
   additionally carry **owner-seeded runtime assignments** in this
   attachment - the owning mod writes the verb into its own cells server-side
   (persisted, synced, a re-seed on content change authoritative over any
   prior state), the fail-closed views then resolve those cells like any
   stored bar so activation carries the cell index as slot context, and
   decision 2's assignability gate keeps players out: their payload is
   rejected outright once the bar is stored. The cell's *content* still
   comes from the provider (item, texture, frame); only the verb rides the
   attachment, and its overlays render alongside the provider's cells.
   Inert preservation extends to
   identity: a stored `bar_id` with no live registration is kept, never
   remapped to a later registrant, re-adopted on re-registration. Loading
   reconciles state with the live registry (append missing assignable bars,
   re-anchor an active id that is neither stored nor registered). The
   single-bar views fail closed (empty) when the active id is not stored -
   which is the normal case while a non-assignable bar is active.
4. **Cycle set and numbering**: the rotation is the built-in window
   (`display.hotbarCount` clamped by `hotbar.maxBars`) followed by all
   registered bars in registration order - registered bars are never clipped by
   the window, so a curios bar cannot be shadowed out. Alt+1..0 indexes this
   full list; an index beyond it is ignored fail-closed. The switch payload
   names the target bar by id; the server validates against its own registry
   and ceiling.
5. **Skip-empty** (`display.skipEmptyBars`, default ON, client-only - the
   name as shipped; the originally settled `hotbar.skipEmptyBars` was renamed
   because a client `[hotbar]` section collides with the server spec's
   section in the config screen's label-key derivation, hotbar's
   `ConfigLocalisationTest` guard): applies
   to wheel cycling only - Alt+N always lands where pressed. A bar counts as
   empty when every slot is unassigned (inert ids count as assigned) or, for a
   non-assignable bar, when its content provider reports no content. If every
   bar in the cycle is empty, cycling falls back to normal stepping so players
   are never trapped.
6. **Cross-side divergence**: the server's registry is authoritative (ADR-003);
   snapshots carry bar ids, so a client missing a mod fails closed for that
   bar rather than misindexing. Registration is a code API (mod setup), not a
   datapack: hotbars are structural and the requirement is "when a mod
   registers".

## Consequences

- ISS and Pseudocore soft-depend on ability-hotbar for the registration call;
  their hotbars appear in the rotation and disappear from assignment screens
  with no further changes here.
- Schema v3 is a third migration step (README ladder `0`/`1`/`2` -> `3`);
  alpha-stage saves are the only ones migrating, so the cost is low now.
- Storing active-by-id costs a lookup where an index sufficed; it buys
  correctness across registration changes and is required once non-assignable
  bars (absent from the attachment) can be active.
- ADR-011 remains the state-model record except its DERIVED-layers clause,
  which this ADR supersedes.
- The content-provider API shipped as hotbar#83/#84 (mod-owned cells render
  through the provider), and the owner-seeded assignment write shipped as
  hotbar#89 - §3 above carries the seeding amendment.
- Ability-to-bar scoping (an ability may belong to exactly one registered
  bar) landed as hotbar#90 as a new axis: it changes neither the menu
  exclusion here nor content placement, so this ADR records only its README
  note.

## Links

hotbar#77 (registration API), hotbar#27 (assignment screen), hotbar#28
(multi-hotbar switching, controls, skip-empty), hotbar#83/#84 (content
provider), hotbar#89 (owner-seeded assignments, §3 amendment), hotbar#90
(ability-to-bar scoping); ADR-011 (state model,
superseded clause), ADR-003 (sync), ADR-004 (versioned persistence),
ADR-007 (spellbook hotbar location).

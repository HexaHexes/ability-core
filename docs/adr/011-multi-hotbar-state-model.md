# ADR-011: Multi-hotbar state model, switch flow and the layer split

Status: accepted

## Context

REQ-HOT-22 (hotbar#28) adds cyclable hotbars with independent assignments,
while the spellbook bar (REQ-SB-*, hotbar#10) and a possible Pseudocore
curios-slot bar are also planned as "extra bars". Three questions had no
recorded answer: how the schema grows without a breaking change (ADR-004
reserved a hotbar-index field for exactly this), whether derived bars join the
cycling rotation, and what a switch costs in latency and code paths. The bar
kind space has two independent axes: content source (ASSIGNED = persisted
slot assignments, DERIVED = read from equipment) x slot action (ABILITY =
core activation pipeline, ADAPTER = provider-specific action).

## Decision

1. **Schema v2, one attachment.** The `bar_state` attachment (id never
   renamed) grows N per-bar slot sets, each with its **own `selectedSlot`**, plus
   a server-owned persisted `activeBar` carried in every snapshot (core#19).
   State allocates `hotbar.maxBars` bars (server ceiling, default 10); a lowered
   ceiling hides higher bars but never destroys their assignments (inert-
   preservation, same rule as unknown ability ids). The v1 flat keys migrate to
   bar 0 / `active_bar = 0` as a normal upgrade-ladder step (hotbar#25).
   Snapshot codec changes bump the payload version, both sides ship together
   (ADR-003).
2. **Count model mirrors slots.** Client preference (`display.hotbarCount`,
   default 3) clamped by the server ceiling `hotbar.maxBars` (default 10) -
   the same two-tier precedence as `slotsPerBar`/`maxSlots` (REQ-CFG-02).
3. **Switch is an optimistic request.** Keybind -> existing InputGate helper
   (hotbar#21) -> client renders the target bar immediately -> serverbound
   `SwitchBarPayload` -> server validates against its own ceiling, sets
   `activeBar`, echoes the snapshot; rejection reverts the optimistic render
   via the ADR-003 resync. The handler never touches `ActivationPipeline`, so
   "no cast, no mode reset, no restriction bypass" holds structurally and is
   asserted server-side. Activation payload is unchanged: it carries
   `abilityId` + `slotContext`, and any slot-membership check resolves against
   the server's `activeBar`.
4. **Layers, not cycle, for derived content.** ASSIGNED bars (any slot action)
   live in the attachment and form the cycling rotation. DERIVED bars (ISS
   spellbook per ADR-007/hotbar#10, future Pseudocore curios bar) are
   independent simultaneous GUI layers over equipment state - they never enter
   the cycle and never appear in the attachment. They share only layout, input
   gating and config primitives with the cycled bars.

## Consequences

- Switching has zero perceived latency in the common case and no new trust in
  the client; the server remains the only authority on `activeBar`.
- Per-bar selection means switching back restores that bar's highlighted slot;
  it also gives loadout presets (REQ-HOT-23, hotbar#29) a natural unit later.
- Custom bar names, loadout presets and per-hotbar mode compatibility stay out
  of scope (hotbar#29); the switch toast is a CLIENT display preference with
  a numeric name until then.
- Keybind defaults for next/previous must be verified against the closed
  keybind landscape (hotbar#4 findings, hotbar#20) before finalising.
- Pseudocore integration ownership remains undecided and is not settled here.

Links: hotbar issues #28 (REQ-HOT-22), #10 (spellbook bar independence), #25
(ladder), #20/#21 (controls), #29 (REQ-HOT-23); core issue #19 (snapshot
covers active index); ADR-003 (sync/versioning), ADR-004 (reserved field),
ADR-007 (spellbook bar location).

# Datapack definition overlays (issue #12)

A datapack can override parts of a **code-registered** ability definition by
id, without a second registry, a dual catalog, or any change to id sync: the
overlay rides **NeoForge registry data maps** attached to the built-in
`ability_core:abilities` registry (the recorded verdict in issue #12;
rationale in `docs/adr/001-registry-based-discovery.md`).

## File location

```text
<data pack>/data/<namespace>/data_maps/ability_core/abilities/<overlay name>.json
```

`<namespace>` is any datapack namespace; `<overlay name>` is yours to choose.
Example:

```json
{
  "values": {
    "pseudocore_curios:capture": {
      "display_name": "pseudocore.skill.capture_renamed",
      "category": "tensura:skill",
      "tags": ["active", "targeted"],
      "data": {
        "accepted_targets": "entity",
        "custom_flag": "on"
      }
    }
  }
}
```

Each value is a **partial definition** in exactly `AbilityDefinition`'s JSON
shape (`display_name`, `icon`, `category`, `activation_type`, `costs`,
`consumption_strategy`, `amount_resolver`, `tags`, `data`, `stats`) - every
field optional. `costs` is an
**array** of `{"resource": ..., "amount": ...}` entries (issue #89);
`consumption_strategy` is a **name** resolving to a registered strategy
(issue #90, default `all` - see `docs/api-surface.md`); `amount_resolver`
is a **name** resolving to a registered amount resolver (issue #103,
default `static` - declared amounts are also the nominal amounts); `stats`
is an **ordered array** of `{"label": <translation key>, "value": <display
text>}` entries for the menu (issue #119 - first is presentation order),
and the `description` entry of `data` is the ability's formal description
slot (read through `AbilityDefinition.description()`); the legacy single
`cost` object is **rejected with an error** naming the replacement
key - silently ignoring it would turn a datapack's cost change into a
no-op.

## Merge rules (applied at lookup)

| Field | Rule |
|---|---|
| `display_name`, `icon`, `category`, `activation_type`, `costs`, `consumption_strategy`, `amount_resolver` | present → **replaces** the base; absent → base kept |
| `tags`, `stats` | present → **replaces** wholesale (never a union; declaration order kept for `stats`); absent → base kept |
| `data` | **merged per-key**: base keys kept, overlay keys win conflicts |

- An overlay that changes nothing is legal (an empty value is a no-op).
- An overlay **cannot resurrect a removed ability**: the loader drops values
  naming ids that are not registered in code (error in the log), and lookup
  only ever *modifies* a base definition.
- Lookups are **live**: `AbilityDiscovery` and the activation pipeline read
  the overlay on every access, so a datapack `/reload` takes effect on the
  next lookup - no listener, no cache, no restart.
- The overlay **syncs to clients** (network codec, non-mandatory), so the
  management menu renders the same merged definitions the server validates
  and charges against.
- Because the pipeline reads the merged view too, a datapack-adjusted `costs`
  list is what gets validated and charged (wholesale replacement - the
  overlay's list is the definition's list), and a datapack-adjusted
  `accepted_targets` is what gates targeting.

## Mastery spec overlays (REQ-CORE-12)

The mastery layer rides the same data-map machinery, one type over:

```text
<data pack>/data/<namespace>/data_maps/ability_core/mastery_spec/<overlay name>.json
```

```json
{
  "values": {
    "irons_spellbooks:fireball": {
      "base_gain": 25.0,
      "gain_handler": "mymod:scale",
      "triggers": ["ACTIVATION", "HIT"],
      "shares": [
        { "kind": "CATEGORY", "id": "irons_spellbooks:fire", "share": 0.5 }
      ],
      "effects": [
        { "axis": "COST", "handler": "mymod:discount" },
        { "axis": "POWER" }
      ],
      "max_level": 60
    }
  }
}
```

Every field is optional. `triggers` are `ACTIVATION` / `HIT` / `KILL` /
`CUSTOM`; `shares` are `ABILITY` / `CATEGORY` / `SUBCATEGORY` targets with
shares in `(0, 1]` summing to at most 1 (self keeps the remainder);
`effects` entries name an axis (`COST` / `CAST_TIME` / `POWER` /
`DURATION` / `RANGE`) with an optional handler name (absent = core's default
curve); `RANGE` and `DURATION` have no applying pipeline in the fleet yet
(declared-only contracts - an overlay can stage them, the pane shows them,
nothing applies them until a domain builds the seam); `max_level` is the
ability's mastery cap (`0` = uncapped, absent
keeps the base's cap - `MasterySpec.DEFAULT_MAX_LEVEL`, 100) and the
server's `mastery.capModes` config decides whether that cap HARDs the
level or SOFTens the XP beyond it. Merge follows the same convention as
definition overlays -
present replaces, absent keeps, **lists replace wholesale** - and an
empty `effects` array removes every effect axis from the ability.

- **Opt-in stays in code**: an overlay only tunes a spec that
  `MasteryDeclarations.declare` registered; an undeclared id has no
  system at all, overlay or not.
- **Fail to base, never to a crash**: unknown triggers/kinds/axes,
  invalid shares and malformed JSON are rejected at load (logged), the
  overlay is dropped, and the code declaration stays in force.
- **Live reads, not synced**: `/reload` applies on the next award or
  effect read; the overlay never leaves the server (awards, effects and
  the query answer are all server-side).

## Mastery level table (REQ-CORE-12)

```text
<data pack>/data/<namespace>/mastery/levels.json
```

```json
{ "levels": [100, 250, 450, 700] }
```

Cumulative XP thresholds: `100` XP reaches level 1, `250` level 2, and
so on; the level pins at the table's length past the last entry - the
table owns both the steps **and** the cap while present, overriding
`mastery.levelCurveBase`, `mastery.levelCurveGrowth` and
`mastery.maxLevel` entirely.

- **Exactly one owner**: zero tables = the config formula, one table =
  it wins, several = an error naming every owner and the formula (a
  conflict is an authoring bug, never a silent pick).
- **Validation at load**: non-empty, finite, non-negative and strictly
  ascending; a bad file logs and is ignored (fail to the formula).

## Scope

- The Java type (`DefinitionOverlay`) is **internal** per
  `docs/api-surface.md` - the JSON schema above is the contract.
- Precedence: overlays are the datapack layer (layer 5 in
  `docs/config.md`'s ladder) - server config and code clamps still sit above
  them.
- Verification: unit fixtures cover schema, merge rules and the discovery
  view; the live end-to-end (drop a JSON, `/reload`, see it in the menu and
  on the client) is part of issue #12's documented dev-run check.

Links: issue #12 (originating contract), `docs/adr/001-registry-based-discovery.md`,
`docs/config.md` (precedence ladder).

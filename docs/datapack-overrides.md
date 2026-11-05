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
shape (`display_name`, `icon`, `category`, `activation_type`, `cost`,
`tags`, `data`) - every field optional.

## Merge rules (applied at lookup)

| Field | Rule |
|---|---|
| `display_name`, `icon`, `category`, `activation_type`, `cost` | present → **replaces** the base; absent → base kept |
| `tags` | present → **replaces** wholesale (never a union); absent → base kept |
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
- Because the pipeline reads the merged view too, a datapack-adjusted `cost`
  is what gets validated and charged, and a datapack-adjusted
  `accepted_targets` is what gates targeting.

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

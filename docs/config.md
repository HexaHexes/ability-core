# Configuration contract (REQ-CFG-01/02)

The shared configuration contract every repository in the ecosystem adopts,
so consumers implement one pattern instead of five (issue #9). Reference
implementation: `CoreConfig` + `ConfigDiagnostics` in this repo.

## Precedence - the ladder

Values resolve in this order; the first layer that carries a *valid* value
wins:

| # | Layer | Mechanism | Owner |
|---|-------|-----------|-------|
| 1 | Safety constraints | code-level clamps (never config) | the mod |
| 2 | Gamerules | `/gamerule` runtime toggles | server operator |
| 3 | World overrides | `<world>/serverconfig/<modid>-server.toml` | server operator, per world |
| 4 | Server config | `<modid>-server.toml` (auto-synced) | server operator |
| 5 | Datapack defaults | `data/<namespace>/<modid>_config/` | datapack authors |
| 6 | Built-in fallback | the spec's default | the mod |

A lower layer never widens a higher one: a datapack default cannot exceed the
server config's ceiling, and no layer can exceed a code clamp.

### The review rule: CLIENT never authoritizes

**Client preferences can never widen a server restriction.** CLIENT values
(layer below every authority - they are not on the ladder at all) are
display and layout only: bar position, opacity, visibility, notices. A PR
that reads a CLIENT value inside a server decision, or lets a client value
choose a number a server also constrains, is rejected on review. When a
value exists on both sides (the hotbar's slot count is the worked example),
the CLIENT value is a *preference clamped by* the SERVER ceiling - never a
peer of it.

## Sides

- **SERVER** - `<modid>-server.toml`, auto-synced to clients (the **only**
  authority channel), per-world overridable via `<world>/serverconfig/`.
  Server policy and ceilings live here.
- **CLIENT** - `<modid>-client.toml`, never synced, never policy. Display and
  layout preferences only.
- **No gameplay value in COMMON or STARTUP** sections: policy that affects
  play must be SERVER so it syncs and re-validates server-side; everything a
  server checks is re-validated server-side regardless of what the client
  believes.
- The datapack layer stays mod-owned per repository:
  `data/<namespace>/<modid>_config/`.

## Diagnostics - loud but safe

`ConfigDiagnostics.readOrDefault(key, read, default)` is the read shape: a
throwing or null value is reported at **WARN with the key and what came
back**, replaced by the default, and offered to the player-notice hook -
never thrown, never mutating the file. NeoForge's own load-time correction
stays the authority; the helper observes, it does not fight
(SPIKE-cfg-1).

**Player-notice hook**: consumers that surface in-game notices call
`ConfigDiagnostics.setNoticeSink(...)` once at client init with their own
renderer. Core renders nothing; without a sink the diagnostic is log-only.
The sink receives an English message - map it to a translation key on your
side if you display it.

## Reload handling (`ModConfigEvent.Loading` / `Reloading`)

The pattern, in two rules:

1. **Read live, never cache.** `ConfigValue.get()` is cheap; a reader that
   calls it on every use needs no re-derivation at all - the next read after
   `ModConfigEvent.Reloading` returns the new value. Core's readers are all
   live (`CoreConfig.refusalLogMode()` reads the knob on every eligibility
   refusal; the fixture asserts one supplier read per refusal, which is the
   reload demonstration).
2. **If you must cache** (a parsed structure, a derived index), re-derive it
   in a `ModConfigEvent.Reloading` handler for your spec - the ISS idiom
   (`SpellConfigManager`). Never keep a stale derived value past a reload.

## Core's reference config

| File | Key | Type | Default | Reader |
|------|-----|------|---------|--------|
| `ability_core-server.toml` | `diagnostics.refusalLog` | `once-per-key` / `every` / `off` | `once-per-key` | `EligibilityChain` (live, per refusal) |
| `ability_core-client.toml` | `diagnostics.playerNotices` | boolean | `true` | consumers' client code (core renders nothing; the reference CLIENT shape) |

## Adoption checklist

1. Register **both** specs from the mod constructor via
   `ModContainer.registerConfig(Type, SPEC, "<modid>-<side>.toml>")` - common
   code; the CLIENT spec simply never loads on a dedicated server.
2. SERVER keys are ceilings/policy; CLIENT keys are display/layout - nothing
   else.
3. Pass the review rule: no server decision reads a CLIENT value.
4. Route reads through `ConfigDiagnostics.readOrDefault`.
5. Read live, or re-derive on `Reloading` - never keep a stale cache.
6. If you surface notices, wire `ConfigDiagnostics.setNoticeSink` client-side.
7. Keep gameplay values out of COMMON/STARTUP sections.
8. Document every key, default and file in your README/config docs.
9. Manual check: hand-edit an invalid value into the TOML -> default applied,
   WARN names the key, the game continues.

Links: issue #9 (originating contract), `docs/sync.md` (transport contract),
`docs/api-surface.md` (stability policy for the `config` package).

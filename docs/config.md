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

### Loud file inspection (REQ-CFG-03)

NeoForge corrects invalid values in the *loaded in-memory model* and logs
only one generic line per file ("Configuration file ... is not correct.
Correcting") - it never names the key, and unknown keys are silently
ignored. `ConfigDiagnostics.inspectFile(spec, path, source)` closes that
gap: it re-reads the file (parse-only - **never writes, never deletes**,
unknown keys intact) and logs at **WARN**:

- every key the spec does not define: `Config <file> contains unknown key
  <key> - ignored, never removed`;
- every value the spec corrects, with key and offending value:
  `Config <file> key <key> holds an invalid value (<bad>); corrected to
  (<value>) (<action>)`. The correction rule itself is NeoForge's, verified:
  an **out-of-range value clamps into range** (999 in a 1..18 spec becomes
  18), an **unreadable value resets to the default** - observation only, the
  live config was already corrected by NeoForge in memory.

Core wires this for its own specs from `ModConfigEvent.Loading` and
`ModConfigEvent.Reloading` (`ConfigInspection`); consumers copy the same
one-liner for theirs. Inspection never throws - a config load always
continues. Core ships **no config rewriter**: the only writer of a config
file is NeoForge itself, and its file model preserves unknown keys.

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

## Migration policy (REQ-CFG-03)

The hard rule: **never fatal, always per-value** - a config problem costs one
value (defaulted, logged), never a crash and never a silently mutated
preference; a wholly corrupt file falls back to full defaults with one loud
error.

- **TOML specs (what ships today)** need no `schema_version`: across a mod
  version bump NeoForge keeps every value whose key still exists, fills new
  keys with defaults, and leaves removed ("obsolete") keys untouched on
  disk - which `inspectFile` *reports by key name* instead of anyone
  deleting them. Corrections are per-value and clamped/defaulted by the
  platform, logged as shown above.
- **Custom formats we own** (none ship today; the AC's versioned-format
  test is conditional on one existing) must carry `schema_version`, a
  `migrate()` that preserves user-set (non-default) values, obsolete-key
  reporting through the shared log format, and the same never-fatal rule.
- When a legacy custom file actually needs converting, use the ISS
  `LegacyConfigConverter` **one-shot command** pattern - never a silent
  in-place rewrite.
- Datapack-layer values are world-owned and replaced wholesale by design -
  out of migration scope.

## Core's reference config

| File | Key | Type | Default | Reader |
|------|-----|------|---------|--------|
| `ability_core-server.toml` | `diagnostics.refusalLog` | `once-per-key` / `every` / `off` | `once-per-key` | `EligibilityChain` (live, per refusal) |
| `ability_core-server.toml` | `diagnostics.malformedPayloadLog` | `once-per-key` / `every` / `off` | `once-per-key` | `ActivateAbilityPayload` (live, per dropped payload) |
| `ability_core-server.toml` | `gameplay.healthCostsCanKill` | boolean | `true` | `BuiltinResources.healthCheck` (live, per health check) |
| `ability_core-server.toml` | `gameplay.disabledResources` | list of ids | `[]` | `ResourceChargeStep` (live, per priced entry) |
| `ability_core-server.toml` | `gameplay.integralCostRounding` | `ceil` / `floor` / `nearest` | `ceil` | `BuiltinResources` integral helpers (live, per charge/refund) |
| `ability_core-server.toml` | `gameplay.persistExternalCooldowns` | boolean | `false` | `CooldownOwners.CoreCooldownTracker` (write at start, read at login) |
| `ability_core-server.toml` | `menu.undeclaredAbilities` | `visible` / `hidden` | `visible` | `VisibilityHandlers.visible` (live, per menu read) |
| `ability_core-server.toml` | `targeting.maxAreaRadius` | number, 1..4096 | `64` | `TargetValidationStep` (live, per area hint) |
| `ability_core-server.toml` | `network.activationRatePerSecond` | integer, 0..1000 | `0` (unlimited) | `ActivateAbilityPayload.handle` (live, per payload) |
| `ability_core-server.toml` | `mastery.enabled` | boolean | `true` | `MasteryAwards` / `MasteryEffects` (live, per award/read) |
| `ability_core-server.toml` | `mastery.globalGainMultiplier` | number, 0..100 | `1.0` | `MasteryAwards` (live, per routed award) |
| `ability_core-server.toml` | `mastery.maxLevel` | integer, 1..1000 | `100` | `MasteryLevels` (live, per level read; formula path - a datapack table overrides it) |
| `ability_core-server.toml` | `mastery.levelCurveBase` | number, 1..1e9 | `100` | `MasteryLevels` (live, per level read; formula path - a datapack table overrides it) |
| `ability_core-server.toml` | `mastery.levelCurveGrowth` | number, 1..10 | `1.15` | `MasteryLevels` (live, per level read; formula path - a datapack table overrides it) |
| `ability_core-server.toml` | `mastery.costReductionPerLevel` | number, 0..1 | `0.005` | `MasteryEffects` COST default curve (live, per cast) |
| `ability_core-server.toml` | `mastery.maxCostReduction` | number, 0..0.95 | `0.25` | `MasteryEffects` COST default curve (live, per cast) |
| `ability_core-server.toml` | `mastery.castTimeReductionPerLevel` | number, 0..1 | `0.005` | `MasteryEffects` CAST_TIME default curve (live, per read) |
| `ability_core-server.toml` | `mastery.maxCastTimeReduction` | number, 0..0.95 | `0.25` | `MasteryEffects` CAST_TIME default curve (live, per read) |
| `ability_core-server.toml` | `mastery.powerIncreasePerLevel` | number, 0..10 | `0.01` | `MasteryEffects` POWER default curve (live, per read) |
| `ability_core-server.toml` | `mastery.maxPowerIncrease` | number, 0..1000 | `0.5` | `MasteryEffects` POWER default curve (live, per read) |
| `ability_core-server.toml` | `mastery.durationIncreasePerLevel` | number, 0..10 | `0.01` | `MasteryEffects` DURATION default curve (live, per read) |
| `ability_core-server.toml` | `mastery.maxDurationIncrease` | number, 0..1000 | `0.5` | `MasteryEffects` DURATION default curve (live, per read) |
| `ability_core-client.toml` | `diagnostics.playerNotices` | boolean | `true` | consumers' client code (core renders nothing; the reference CLIENT shape) |

**Core's layer-1 clamps (code, never config):** a target-hint param string
over 512 characters is dropped before it is parsed, and every ranged key
above carries a spec range the platform clamps into - no layer can widen
either.

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

# API surface policy

This document defines the stable, consumer-facing API of `ability-core`,
the breaking-change and deprecation policies, and the dependency coordinates
for the four consumer repositories.

## Supported packages

| Package | Stability | Purpose |
|---|---|---|
| `org.bitwisemadness.abilitycore.api` | **Stable** — public API | Data types: `AbilityDefinition`, `ActivationRequest`/`Result`/`Outcome`/`Source`, `TargetContext`, `ActivationType`, `ChargingModel`, `Affordability`, `ResourceCost`, `ResourceConsumer`, overlay contract: `AbilityAvailability`, `Metric`, `AvailabilityState`, `AvailabilityProvider`, `AvailabilityProviders`, cooldown contract: `CooldownContract`, `CooldownOwners`, `CooldownTracker`, icon projection: `IconProvider`, `IconProviders`, resource pairs: `ResourceProvider`, `ResourceProviders`, `ResourceConsumers` |
| `org.bitwisemadness.abilitycore.registry` | **Stable** — public API | `AbilityRegistries` (registry key + `registry()` + `isReady()`) |
| `org.bitwisemadness.abilitycore.discovery` | **Stable** — public API | `AbilityDiscovery` (presence, enumeration, filtering) |
| `org.bitwisemadness.abilitycore.activation` | **Stable** — public API | `ActivationPipeline`, `ActivationHandler`, `ActivationHandlerRegistry`, `EligibilityChain`, `EligibilityRule`, `ExternalCooldownStep`, `ResourceChargeStep`, `TargetResolver`, `TargetValidationStep` |
| `org.bitwisemadness.abilitycore.sync` | **Stable** — public API | `ActivateAbilityPayload`, `NetworkRegistration`, `AbilitySyncNeededEvent`, `SyncEventWiring` |
| `org.bitwisemadness.abilitycore.config` | **Stable** — public API | `CoreConfig` (reference SERVER/CLIENT specs, `RefusalLogMode`), `ConfigDiagnostics` (loud-but-safe reads, notice hook) |

**Everything not listed above is internal and subject to change without notice.**

## Extension points

Consumers extend core through exactly eight mechanisms:

1. **Registry registration** — providers register `AbilityDefinition` entries
   on their own mod bus via `RegisterEvent` for `AbilityRegistries.ABILITY_REGISTRY_KEY`.
2. **Handler declaration** — providers register `ActivationHandler` +
   `ChargingModel` (+ optional `ChargeAction`) via
   `ActivationHandlerRegistry.register(...)`.
3. **Eligibility rules** — policy providers register `EligibilityRule`
   instances (source-scoped) via `EligibilityChain.register(...)` on
   `AbilityCore.ELIGIBILITY` or a composed child chain.
4. **Sync events** — consumers listen for `AbilitySyncNeededEvent` on the
   NeoForge game bus and send their state payloads. The payload inventory,
   serverbound budget and version-bump checklist live in
   [`docs/sync.md`](sync.md).
5. **Overlay providers** — providers declare an `AvailabilityProvider` per
   ability via `AvailabilityProviders.declare(...)` to feed the bar's
   cooldown/cost/availability overlay (REQ-HOT-08).
6. **Cooldown ownership** — providers declare per-ability cooldown ownership
   once via `CooldownOwners.declareNative(id)` (optionally with a read-through
   `NativeProjection`) or `declareExternal(id, ticks[, tracker])`; exactly one
   declaration per id (see the cooldown contract below).
7. **Icon projection** — providers declare an `IconProvider` per ability via
   `IconProviders.declare(...)` for per-player dynamic icons; the resolution
   order (provider → definition `icon` → consumer placeholder) is the
   consumer contract (see the icon projection contract below).
8. **Resource access & consumption** — addons declare a pair per currency:
   `ResourceProviders.declare(id, check)` **first**, then
   `ResourceConsumers.declare(id, charge)` (the pairing rule enforces the
   order), so their own resource can validate and charge an ability's cost;
   core ships four built-in pairs (see the resource-pairs section below), and
   the `has`/`get` queries support registration-time cost selection.

## Overlay data contract (REQ-HOT-08)

The bar renders an ability's cooldown/cost/availability overlay from one
provider-neutral tuple, `AbilityAvailability`, and never from a provider class:

| Metric | Type | Meaning |
|---|---|---|
| `cooldown` | `Metric<Double>` | Cooldown progress `0..1` (0 = ready), or `Metric.unknown()` |
| `cost` | `Metric<ResourceCost>` | Declarative resource cost, or `Metric.unknown()` |
| `availability` | `AvailabilityState` | `Available` / `Unavailable(reason)` / `Unknown` |

**Option-typed, never a wrong value.** Every metric is explicit about absence:
a provider that does not expose a metric returns `Metric.unknown()` (or
`AvailabilityState.Unknown`), and the overlay renders *no overlay* for it -
never a guessed grey-out or a zeroed cooldown. `Metric.known(0.0)` means
"ready"; `Metric.unknown()` means "we cannot tell" - these are deliberately
distinct. `AbilityAvailability.allUnknown()` is the synthetic no-metric tuple.

**Consumer mapping notes:**

- **Providers (ISS, Tensura, battlewills)** map their *already-synced* native
  state into the tuple once, at `AvailabilityProvider.availabilityFor(...)`.
  Read only synced or contract-synced values (the client never holds
  authority); return `unknown` for any metric the provider does not expose.
- **The bar (consumer)** looks up `AvailabilityProviders.availabilityFor(player,
  id)`. `Optional.empty()` (no provider declared) means render no overlay -
  never crash. A provider returning `null` is logged and degraded to
  `allUnknown()`. The consumer imports only these `api` types, never a provider
  class.
- **Side discipline:** the tuple is client-*renderable* but server-authoritative
  in origin; providers must not mutate state or derive authority the client was
  not given.

## Icon projection contract (core#62)

`AbilityDefinition.iconId` is static per definition - one texture for every
player. A provider whose icon varies with *player* state (the tier of core
actually carried, an enchanted stack) projects it through **`IconProviders`**,
the exact mirror of the overlay registry: `declare(id, provider)` /
`iconFor(player, id)` / `has` / `remove`, one provider per id (duplicates
fail loudly), `Optional.empty()` = no provider - so a consumer never branches
on `ModList.isLoaded` for *icon data*.

**Resolution order (the consumer contract - deterministic, never null, never
throws):**

1. a provider is present and returns a value → that value;
2. else the definition's static `icon`;
3. else the consumer's own placeholder.

```java
ResourceLocation icon = IconProviders.iconFor(player, id).orElse(null);
if (icon == null) {
    icon = AbilityDiscovery.get(id).map(AbilityDefinition::iconId).orElse(null);
}
if (icon == null) {
    icon = PLACEHOLDER; // the consumer's fallback texture
}
```

- **Client render-time, client-state only**: `iconFor` runs where the bar
  renders and reads only client-known state (synced stacks/attachments). The
  reference consumer shape reads exactly that - the example above takes the
  already-synced `player`; declaring a provider never triggers or requires a
  server sync. Icons are display-only, resolved by the texture lookup at
  draw time: no payload, no server trust.
- **Failure falls down the order**: a provider that throws or returns null
  is caught, logged **once per ability** (render time calls it every frame),
  and resolution continues at step 2 - a broken provider can never crash or
  blank a slot.
- **Optional surface**: a consumer that declares nothing behaves exactly as
  the static `iconId` path - nothing in core registers a provider
  (grep-gated by `IconProvidersTest`).

Links: hotbar#26 (the render consumer), pseudocore-curios#69 (first
per-player consumer: press = the carried core's texture), core#62
(originating contract).

## Cooldown contract (REQ-CORE-05)

Exactly one cooldown owner per ability, declared once (`CooldownOwners`);
dual or malformed declarations fail loudly at declaration time:

| Owner | Declared by | Checked by | Reaching the client |
|---|---|---|---|
| `NATIVE` (default) | `declareNative(id)` (+ optional `NativeProjection`) | the provider's own pipeline (ISS `PlayerCooldowns`, Tensura per-skill) | the provider's **own** sync (ISS already syncs cooldowns — hotbar#2 §3); core sends nothing |
| `EXTERNAL` | `declareExternal(id, ticks[, tracker])` | `ExternalCooldownStep`, pre-activation (`REFUSED_COOLDOWN`), start post-activation | a **consumer snapshot** payload (ADR-003) or the overlay tuple — still no core packet |

**Reads** go through `CooldownContract.remaining(...)` / `isOnCooldown(...)`:

- `EXTERNAL` answers from the declared tracker — the same store the pipeline
  gate reads, so gate and query can never disagree.
- `NATIVE` **with** a projection answers with the provider's own remaining
  *exactly* — core stores the function, never the state (parity by
  construction; nothing to re-sync).
- `NATIVE` **without** a projection answers *unknown* (`Optional.empty()`) —
  never a guessed "ready" and never a claimed cooldown; the provider's
  pipeline is the gate.
- **Start has exactly one site**: the pipeline's post-activation
  `ExternalCooldownStep.startExternalCooldown` (never at initiation — ISS
  starts cooldowns inside `castSpell`). The contract has no public `start`;
  a second start site would break native start timing (AC2/AC4).

**No duplicate packets**: core's compiled payload inventory is serverbound
intent only, pinned mechanically by `SyncContractTest`
(`corePayloadInventoryIsServerboundIntentOnly`) and inventoried in
[`docs/sync.md`](sync.md).

**Persistence decision** (core#16): core-owned `EXTERNAL` cooldowns are **not
persisted** — the built-in store recomputes from runtime state and clears on
restart (recorded in `CooldownTracker`'s javadoc). Tying them to persisted
resource state, if ever required, is filed as its own issue.

Links: core#16 (originating contract), ADR-002 (exclusivity pattern),
`docs/sync.md` (no-resync inventory); rendering reads the overlay contract
section above, never this facade.

## Breaking change definition

A change is **breaking** if it removes or changes the signature of any
public method, record component, enum constant, or field in the packages
listed above; or if it changes the wire format of any registered payload
(version string must be bumped and shipped to both sides together).

Breaking changes require:
1. The `breaking` label on the PR (CI's version-bump check forces a major
   or minor version bump — never a patch).
2. One minor cycle of `@Deprecated` with a replacement before removal
   (except for pre-1.0 alphas where direct removal is permitted).

## Dependency coordinates

The reference pattern, verified verbatim against every current consumer's
`build.gradle` (2026-11-05):

```groovy
repositories {
    mavenLocal()
    maven {
        name = 'AbilityCorePackages'
        url = uri('https://maven.pkg.github.com/HexaHexes/ability-core')
        credentials {
            username = System.getenv('GITHUB_ACTOR') ?: project.findProperty('gpr.user')
            password = System.getenv('GITHUB_TOKEN') ?: project.findProperty('gpr.key')
        }
    }
    mavenCentral()
}

def abilityCore = 'org.bitwisemadness.abilitycore:ability_core:<version>'
compileOnly abilityCore
testImplementation abilityCore
```

`compileOnly`, not `implementation`: core ships as its own mod at runtime, so
consumers compile against the artifact but never shade it — `testImplementation`
covers fixtures that reference contract types. `mavenLocal()` first is the
template idiom for local-publish testing (`gpr.user`/`gpr.key` in
`~/.gradle/gradle.properties`).

### The four consumers (verified 2026-11-05)

| Repo | Repository block | Exact pin | Status |
|---|---|---|---|
| `ability-hotbar` | `AbilityCorePackages`, as above | `org.bitwisemadness.abilitycore:ability_core:1.0.0-alpha.15` | live |
| `tensura-iss-integration` | `AbilityCorePackages`, as above | `org.bitwisemadness.abilitycore:ability_core:1.0.0-alpha.9` | live |
| `ability-tensura` (formerly `ability-battlewills`) | `AbilityCorePackages`, as above | `org.bitwisemadness.abilitycore:ability_core:1.0.0-alpha.9` | live |
| `tensura-better-spellcasting-bridge` | *none yet* | *none yet* | not a build-level consumer: `build.gradle` has no repository block, dependency or source import today (the template already grants `packages: read` and `agents/REPO.md` declares core required) — bridge adopts the pattern above verbatim when its core-dependent implementation starts |

The group is `org.bitwisemadness.abilitycore` (core's `mod_group_id`) while
the artifact id stays `ability_core`; the repository path is
`HexaHexes/ability-core`.

**Secret scoping:** `GITHUB_TOKEN` must be injected scoped to the Gradle
step only (never at job level — it silently changes what `actions/checkout`
and `gh` authenticate as). The org secret `PACKAGE_READ_TOKEN` provides
cross-repo read access; GitHub returns 404 (not 401) when credentials are
missing, so an unauthenticated consumer fails with a message that looks
like a mistyped coordinate.

**Version pinning:** consumers pin the exact alpha version they were
verified against (e.g. `1.0.0-alpha.9`). Bumps are deliberate, never
implicit (see AGENTS.md "Pin the exact version").

## Resource pairs (REQ-CORE-04, issue #88)

A currency's payment has two halves, declared as a pair:

| Half | Interface | Role |
|---|---|---|
| read | `ResourceProvider.check(abilityId, player, amount)` | affordability verdict for one cost entry — a pure server-side read; the snapshot carries the entry's amount (no re-derivation) |
| write | `ResourceConsumer.charge(abilityId, player, snapshot)` | deducts the validated snapshot after ACTED (EXTERNAL chargingModel only — ADR-002) |

- **Pairing rule**: `ResourceConsumers.declare` requires `ResourceProviders.has(id)`
  and fails with the resource id otherwise — validation and deduction can
  never drift apart. Exactly one provider and one consumer per resource id;
  duplicates fail loudly on both sides.
- **Mod-gated declarations (auto-disable, issue #92)**:
  `declare(id, half, requiredModId)` — when the mod backing the resource's
  state is not loaded, the half is **skipped with a debug line** (absence is
  expected, not misuse: no throw, and the pairing IAE does not fire), `has()`
  reads false, and abilities costing it fail closed with `no_provider` — that
  chain *is* the auto-disable. Null `requiredModId` is ungated (core's
  built-ins use it). Declare at mod construction: ModList is populated before
  construction.
- **Timing contract**: declare both halves at mod construction; ability
  definitions register at `RegisterEvent`, which fires after all mods are
  constructed — so registration-time cost decisions are order-safe.
- **Built-ins** (registered by core at construction): `ability_core:health`,
  `ability_core:hunger`, `ability_core:experience_points`,
  `ability_core:experience_levels` — thin player adapters over pure,
  fixture-tested helpers (no player fixture exists in core).
- **Deliberate `free` (issue #93)**: the fifth built-in — `ability_core:free`
  always answers SUFFICIENT with **amount 0** (the check's requested amount is
  ignored: the truth is always "nothing") and its charge is a logged no-op.
  An ability uses it **only by naming it** as its cost; `cost == null` +
  EXTERNAL still refuses undeclared — there is **no implicit free anywhere**.
  With multi-cost (issue #89's composition rule): `free` is the payment only
  when *every* entry is `free`; a non-free sibling must be fully valid or the
  ability fails — `free` never masks an invalid sibling.
- **Fail-closed pipeline** (`ResourceChargeStep`, in order): cost without a
  provider → `ability.refused.unaffordable.no_provider`; without a consumer →
  `...no_consumer`; a null or UNKNOWN check refuses — nothing charged.
- **Conditional selection**: `ResourceProviders.has` /
  `ResourceConsumers.has` let an addon pick its costs at registration time
  (pseudocore#174: experience points unless aura *and* magicule exist).

Links: core#88 (this contract), core#89 (multi-cost), core#90 (strategies).

## Leak gates (mechanism)

Two CI gates enforce the bounded surface above (both in `src/test/`, both run
in every `junit` job, both fail the build on any hit):

- **`ApiLeakGateTest`** — the *upstream* boundary (REQ-XREF-03, ADR-001/005):
  scans the compiled class files of all supported packages for byte-level
  references to upstream mod types (`io/redspace/ironsspellbooks/`,
  `io/github/manasmods/`, `net/bettercombat/`, `net/kayn/better_spellcasting/`).
  Upstream knowledge belongs exclusively in consumer-side provider adapters.
  `net/neoforged/fml/ModList` is scoped separately (issue #92): forbidden in
  `discovery` and `registry` (the core#14 AC — presence is never inferred for
  registration), allowed in `api` solely for **declaration-time** optional
  gates (`declare(..., requiredModId)`).
- **`ApiSurfaceSignatureGateTest`** — the *internal* boundary (REQ-CORE-09):
  loads every compiled class in a supported package (without initialization)
  and walks its consumer-facing **signatures** — declared public/protected
  methods, fields, constructors and supertypes, including generic type
  arguments, type-variable bounds, and array/wildcard components. A signature
  naming a type outside the supported set (today: core's root package —
  `AbilityCore` — or any future internal package) fails with the exact member
  that leaks it. **Signatures only, deliberately**: internal types in method
  *bodies* (`AbilityCore.MOD_ID`, `AbilityCore.PIPELINE`) are implementation,
  not surface, and never reach a consumer's compile.

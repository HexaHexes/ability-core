# API surface policy

This document defines the stable, consumer-facing API of `ability-core`,
the breaking-change and deprecation policies, and the dependency coordinates
for the four consumer repositories.

## Supported packages

| Package | Stability | Purpose |
|---|---|---|
| `org.bitwisemadness.abilitycore.api` | **Stable** - public API | Data types: `AbilityDefinition`, `ActivationRequest`/`Result`/`Outcome`/`Source`, `ActivationRequests` (slot gate), `TargetContext`, `ActivationType`, `ChargingModel`, `Affordability`, `ResourceCost`, `ResourceConsumer`, `RefundableConsumer`, overlay contract: `AbilityAvailability`, `Metric`, `AvailabilityState`, `AvailabilityProvider`, `AvailabilityProviders`, cooldown contract: `CooldownContract`, `CooldownOwners`, `CooldownTracker`, icon projection: `IconProvider`, `IconProviders`, resource pairs: `ResourceProvider`, `ResourceProviders`, `ResourceConsumers`, strategies: `ConsumptionStrategies`, pricing: `AmountResolvers`, modes contract: `AbilityModes`, `ModeOwners`, `ModeContract`, `ModeChangeRequest`, `ModeChangeHandler`, `ModeChangeHandlers`, unlock contract: `AbilityUnlocks`, `AbilityUnlocks.UnlockProvider` |
| `org.bitwisemadness.abilitycore.registry` | **Stable** — public API | `AbilityRegistries` (registry key + `registry()` + `isReady()`) |
| `org.bitwisemadness.abilitycore.discovery` | **Stable** — public API | `AbilityDiscovery` (presence, enumeration, filtering) |
| `org.bitwisemadness.abilitycore.activation` | **Stable** — public API | `ActivationPipeline`, `ActivationHandler`, `ActivationHandlerRegistry`, `EligibilityChain`, `EligibilityRule`, `ExternalCooldownStep`, `ResourceChargeStep`, `TargetResolver`, `TargetValidationStep` |
| `org.bitwisemadness.abilitycore.sync` | **Stable** — public API | `ActivateAbilityPayload`, `NetworkRegistration`, `AbilitySyncNeededEvent`, `SyncEventWiring` |
| `org.bitwisemadness.abilitycore.interop` | **Supported (optional integration)** | `leylines.LeyLinesResources` — declare one adopter-chosen Sky's Ley Lines currency as a resource pair (core#96); the only package allowed to reference an optional upstream mod, exclusively behind the `ModList` gate, and no public signature names an upstream type (both gates green) |
| `org.bitwisemadness.abilitycore.config` | **Stable** — public API | `CoreConfig` (reference SERVER/CLIENT specs, `RefusalLogMode`), `ConfigDiagnostics` (loud-but-safe reads, notice hook) |

**Everything not listed above is internal and subject to change without notice.**

## Extension points

Consumers extend core through exactly twelve mechanisms:

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
9. **Consumption strategy declaration** — addons declare a named selection
   function via `ConsumptionStrategies.declare(name, strategy)` for an
   ability's `consumption_strategy` field (see the strategies section below).
10. **Cost amount resolution** — addons declare a named per-request pricer
    via `AmountResolvers.declare(name, resolver)` for an ability's
    `amount_resolver` field (see the amount-resolution section below).
11. **Ability-mode contract** — providers declare an ability's mode cycle via
    `AbilityModes.declare(id, modes)`, own its live state via
    `ModeOwners.declareNative(id[, projection])` (readable through
    `ModeContract.currentMode`), and register a `ModeChangeHandler` for the
    server-side mode-change intent (see the modes section below).
12. **Unlock providers** — providers declare a per-player unlock answer via
    `AbilityUnlocks.declare(id, provider)` so consumers read "does the player
    have this ability?" through `AbilityUnlocks.isLearned(...)` without
    upstream types; the provider answers live and owns its upstream event
    side effects (see the unlock contract below).

## Overlay data contract (REQ-HOT-08)

The bar renders an ability's cooldown/cost/availability overlay from one
provider-neutral tuple, `AbilityAvailability`, and never from a provider class:

| Metric | Type | Meaning |
|---|---|---|
| `cooldown` | `Metric<Double>` | Cooldown progress `0..1` (0 = ready), or `Metric.unknown()` |
| `cost` | `Metric<ResourceCost>` | Declarative resource cost, or `Metric.unknown()` — for multi-cost abilities (issue #89) this is the provider's single representative entry, or unknown |
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
  An ability uses it **only by naming it** as a cost entry; an empty `costs`
  list + EXTERNAL still refuses undeclared — there is **no implicit free
  anywhere**. With multi-cost (issue #89's composition rule): `free` is the
  payment only when *every* entry is `free`; a non-free sibling must be fully
  valid or the ability fails — `free` never masks an invalid sibling.
- **Sky's Ley Lines (issue #96, first part)**: `LeyLinesResources.declare(currencyId)`
  (the `interop` package) declares the pair for **one adopter-chosen** Ley
  Lines-registered currency — core never enumerates or hardcodes which energy
  types the mod registers (the agnosticism rule; the leak gate scans for their
  package in the contract ones). The resource id **is** `currencyId.toString()`
  (a costs entry reads `"resource": "technomancy:aether"`). Read half:
  `quoteAt` at the player's position → account balance ≥ amount; outside a
  usable nexus field or a fractional amount (their balances are whole units)
  → INSUFFICIENT, refused pre-activation. Write half: `spendAt` post-ACTED;
  a failed spend is logged, never re-run (their contract leaves balances
  untouched). Absent mod → skip with a debug line **before any upstream class
  links** (the bridge resolves only after the `ModList` gate), then the #92
  `no_provider` chain owns downstream. Tested on `0.5.4.121012` (vendored
  unmodified in `libs/` with its LGPL-2.1 license — `compileOnly`, never
  shaded; see `libs/README.md`).
- **Refunds (issue #107)**: `RefundableConsumer extends ResourceConsumer`
  adds `refund(abilityId, player, snapshot)` — the pair owner's inverse of its
  own charge. `ResourceConsumers.refund(...)` dispatches with the **exact
  charged snapshot** (never a recomputed amount, so charge and refund cannot
  drift) and answers `false` with an **error log** — nothing thrown at the
  player — for an absent consumer, a non-refundable consumer, or a refund
  that throws (caught at the dispatch, mirroring charge's posture).
  Integrators that route payments through someone else's consumer *ask* the
  owner; they cannot invert a charge they did not implement. Declaration is
  unchanged (the owner decides at declare time); all built-in pairs are
  refundable, and `free` refunds as a logged no-op.
- **Fail-closed pipeline** (`ResourceChargeStep`, all-or-nothing per entry,
  issue #89): a cost entry without a provider →
  `ability.refused.unaffordable.no_provider`; without a consumer →
  `...no_consumer`; a null or UNKNOWN check refuses — **nothing charged for
  any entry** (the full list validates before anything is stashed).
- **Conditional selection**: `ResourceProviders.has` /
  `ResourceConsumers.has` let an addon pick its costs at registration time
  (pseudocore#174: experience points unless aura *and* magicule exist).

Links: core#88 (this contract), core#89 (multi-cost), core#90 (strategies).

## Consumption strategies (REQ-CORE-04, issue #90)

An ability's `consumption_strategy` field names **how** its `costs` entries
are consumed. The name resolves through `ConsumptionStrategies` at
definition registration — an unknown name fails loudly there (definitions
register at `RegisterEvent`, strategies declare at construction, so
resolution is order-safe).

**Selection vs validation.** A strategy only *selects*: it returns the
ordered subset of entries to charge (empty = the pipeline refuses
unaffordable — nothing charged). Core then validates **exactly the selected
entries** fail-closed and charges them exactly once, in returned order.
Validation scope belongs to the strategy — a selector that skips an entry is
never refused for it — but a selected entry must be one of the definition's
declared costs (an undeclared selection is a contract violation → refusal).

| Name | Semantics |
|---|---|
| `all` (default) | select every entry → validate all → charge in list order. Existing JSON resolves here. |
| `random` | re-roll among affordable: consult each entry, uniform pick among SUFFICIENT ones; none affordable → empty selection → refusal. |
| `in_order` | recorded verbatim: *"Consume from each in order, till the cost is satisfied; if cost can not be satisfied, fail before consuming."* Operational reading (open for review): consult in list order, **early-abort at the first uncovered entry** (empty selection → refusal, later entries never consulted); full pass → all entries in list order. |

**Custom strategies are registerable code** — same timing contract as
providers/consumers:

```java
ConsumptionStrategies.declare("mymod:enchantment_pick",
        (request, player, entries, check) -> {
            // request-aware: the request carries slot/source/target context
            boolean hasAuraEnchant = /* inspect the item at request.slotContext() */ false;
            if (hasAuraEnchant) {
                return List.of(entries.get(0)); // only the aura entry pays
            }
            return entries; // otherwise both halves
        });
```

The planning example (pseudocore#174): a datapack names
`mymod:enchantment_pick`, the strategy reads the item at
`request.slotContext()`, checks its enchantments, and returns only the aura
entry (or only magicule, or both). `check` is a read-only affordability
consult (missing provider / null verdict read as UNKNOWN — not affordable);
strategies never charge — charging stays at core's single EXTERNAL site.

**Overlay**: `consumption_strategy` mirrors the definition shape — present
replaces wholesale, absent keeps the base; an overlay naming a strategy
nobody declared refuses fail-closed at runtime (`REFUSED_UNAVAILABLE`).

## Cost amount resolution (REQ-CORE-04 p4, issue #103)

An ability's `amount_resolver` field names **how much** each declared
`costs` entry costs *for one activation*. The declared amounts stay the
wire contract and the nominal display value; a resolver re-prices the list
per request — the capture case (pseudocore#174: the aimed mob's EP, split
by payment enchantments), whose price does not exist before the request.

```java
AmountResolvers.declare("mymod:ep_share",
        (declared, request, player, resolvedTarget) -> /* price for this request */ declared);
```

- **Timing contract** (identical to providers/consumers/strategies):
  declare at mod construction; definitions register at `RegisterEvent`, so
  a name nobody declared fails loudly at registration
  (`AbilityDefinition.validateOrThrow`).
- **Resolution runs before selection** in `ResourceChargeStep` (after the
  empty-costs gate): the strategy receives the *priced* entries, so the
  built-in consults (`random`, `in_order`) and custom selectors observe
  this request's amounts. Core then validates, snapshots and charges
  through the unchanged amount-parameterized path —
  `provider.check(id, player, amount)` receives the priced amount, and the
  consumer receives that `Affordability` snapshot verbatim.
- **Mirror contract**: the resolver returns a same-size list mirroring the
  declared resource ids in order, carrying amounts `>= 0` and finite.
  Every violation — unknown or vanished name, null result, size/order/id
  mismatch, an invalid amount, a throwing resolver — is logged and refuses
  `REFUSED_UNAVAILABLE` with nothing charged. (`ResourceCost`'s own guard
  rejects negative/non-finite amounts at construction inside the resolver,
  so those surface on the throw row with the same fail-closed outcome.)
- **`resolvedTarget`** is the server-stashed
  `TargetValidationStep.RESOLVED_ENTITY` value for entity-hint targets
  (`null` otherwise) — opaque by doctrine; the resolver owns the cast,
  exactly as handlers and providers do, and a resolver needing the player
  (e.g. `Self`) has `player`.
- **Built-in `static`**: identity, pre-registered and default — existing
  definitions and datapacks are byte-identical (the encoder omits the field
  when it equals the default). Re-pricing `ability_core:free` is inert by
  its contract (its check ignores the amount; its charge is a logged no-op).
- **Display is out of contract**: `AbilityAvailability.cost` stays a
  provider-side projection (REQ-HOT-08) — a per-request estimate is the
  consumer's own surface, not core's.

**Overlay**: `amount_resolver` mirrors the definition shape — present
replaces wholesale, absent keeps the base; an overlay naming a resolver
nobody declared refuses fail-closed at runtime (`REFUSED_UNAVAILABLE`).

## Ability modes (issue #113)

Three layers own three different mode facts (the design's spine, from
core#112's findings):

| Layer | Owns |
|---|---|
| the provider (ARR/ManasCore for Tensura) | authoritative live mode: transitions, learned sets, effects |
| the consumer (hotbar) | the per-slot mode **setting** and the keybind UX |
| core | the provider-neutral contract: declaration, projection, request/intent shapes |

- **Declaration**: `AbilityModes.declare(abilityId, List<Mode>)` at setup -
  `Mode(id, nameKey)`, ordered, first = default, one declaration per id
  (loud ownership; blank and duplicate ids rejected). The registry shape
  was chosen over a definition component (core#111's rejected alternative):
  modes are provider setup-time knowledge, not datapack metadata, and a
  per-mode `nameKey` carries display text a plain string field cannot.
- **Live state**: `ModeOwners.declareNative(id[, projection])` read through
  `ModeContract.currentMode(player, id)` - core stores the function, never
  the state; undeclared or projection-less abilities answer empty
  (unknown, never a guessed mode, REQ-HOT-08). This is deliberately
  distinct from the hotbar slot's stored *setting* - two questions, two
  owners.
- **Activation context**: `ActivationRequest.modeContext()` - advisory, like
  the target hint - travels with the intent, on the wire as
  `ActivateAbilityPayload`'s mode field (channel version 3). Two slots of
  one ability can activate with different modes while the provider keeps a
  single live state; providers validate the value against their own
  declared modes, and a blank mode builds no context.
- **Mode-change intent**: `ModeChangeRequest(abilityId, slotContext)` routed
  through `ModeChangeHandlers.run(...)` - the handler shape minus charging:
  a mode change never charges, cools down or casts, and an unclaimed id
  refuses `REFUSED_NO_ADAPTER`. Transport is the consumer's own
  serverbound payload (the per-consumer inventory in `docs/sync.md`) -
  core routes, consumers send, and the sender applies its state update
  only on ACTED.
- **Client sync**: declarations run at mod construction on both sides
  (ADR-003's same-construction doctrine), so client reads need no payload.
  Should a provider's mode set turn out to be server-config-derived (the
  carried core#112 unverified item 1), its content rides the future
  REQ-CORE-08 catalog payload that carries definition content (recorded on
  core#19) - core sends no game data today, and no dedicated modes payload
  exists by design.

## Slot gate (issue #87)

Abilities that act on a specific cell of a content-owned bar share one
wire-level guard — `ActivationRequests.requireSlot(request, slotCount)`
returns either the validated index or the refusal to return:

- **Missing `slotContext` → refuse, fail-closed.** There is deliberately
  **no "active slot" fallback**: a request without a cell never guesses
  which target is meant.
- **Out-of-range / stale cell → refuse.** The caller passes its **live**
  slot count (re-read per call), so a stale seeded cell refuses instead of
  acting on a neighbour.
- **Outcome vocabulary** (mirrors pseudocore-curios' verified `press`
  handler): both failures refuse `REFUSED_UNAVAILABLE`; domain refusals
  (cooldown, gating, unreachable) stay in the handler, which maps them with
  its own vocabulary.

Usable from `ChargingModel.NATIVE` handlers; carries no upstream-mod types.
The consumers (curios' `press`, the forthcoming iss-hotbar `cast`) adopt it
in their own repos with their behaviour unchanged.

## Unlock contract (issues #116/#117)

"Does this player have this ability?" is a provider-declared, option-typed
query — the `AvailabilityProviders` mirror with a boolean answer:

| Piece | Shape |
|---|---|
| Declaration | `AbilityUnlocks.declare(abilityId, provider)` at mod setup — exactly one provider per id, duplicates fail loudly |
| Query | `AbilityUnlocks.isLearned(player, abilityId)` → `Optional<Boolean>`: `true` = learned, `false` = not, **empty = unknown** (undeclared id, abstaining provider, or a null-returning violation — logged once) |
| Provider | `UnlockProvider.learnedFor(player, id)` — upstream types live *inside* implementations only; no signature here names another mod (leak-gate rule) |

**Discipline: unknown stays unknown.** Consumers render and validate
`empty` as unknown — never a guessed boolean (REQ-HOT-08).

**The provider event-subscription invariant.** Providers answer *live* —
the query never caches, so it cannot disagree with the world. Providers act
on their upstream's own learn/unlock events as the **side-effect** half
(clearing stale assignments, refreshing dependent state), and **reconcile at
login** so any event-less mutation path (commands, datapack resets) is
corrected. Reference upstream: ManasCore's `UNLOCK_SKILL` / `REMOVE_SKILL`
events are both cancelable pre-state notifications carrying the skill
instance and entity. Core stores the provider function, never learned
state: no per-player duplication of upstream truth, and no payload in v1 —
upstream learned state is client-readable today (evidenced), so a provider
that cannot answer client-side abstains to unknown rather than core
syncing a second copy.

**Scope:** learned/unlearned only. The locked-list read (a separate
upstream concept) is deferred until its semantics are pinned — use-gate vs
acquisition-gate (findings #116, unverified item 1).

**Consumers:** the hotbar menu and assignment validation (ability-hotbar#135) —
the first reader; providers are the first writer (ability-tensura's gate).

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

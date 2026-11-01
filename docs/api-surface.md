# API surface policy

This document defines the stable, consumer-facing API of `ability-core`,
the breaking-change and deprecation policies, and the dependency coordinates
for the four consumer repositories.

## Supported packages

| Package | Stability | Purpose |
|---|---|---|
| `org.bitwisemadness.abilitycore.api` | **Stable** — public API | Data types: `AbilityDefinition`, `ActivationRequest`/`Result`/`Outcome`/`Source`, `TargetContext`, `ActivationType`, `ChargingModel`, `Affordability`, `ResourceCost`, `ResourceConsumer`, overlay contract: `AbilityAvailability`, `Metric`, `AvailabilityState`, `AvailabilityProvider`, `AvailabilityProviders` |
| `org.bitwisemadness.abilitycore.registry` | **Stable** — public API | `AbilityRegistries` (registry key + `registry()` + `isReady()`) |
| `org.bitwisemadness.abilitycore.discovery` | **Stable** — public API | `AbilityDiscovery` (presence, enumeration, filtering) |
| `org.bitwisemadness.abilitycore.activation` | **Stable** — public API | `ActivationPipeline`, `ActivationHandler`, `ActivationHandlerRegistry`, `EligibilityChain`, `EligibilityRule`, `ExternalCooldownStep`, `ResourceChargeStep`, `TargetResolver`, `TargetValidationStep` |
| `org.bitwisemadness.abilitycore.sync` | **Stable** — public API | `ActivateAbilityPayload`, `NetworkRegistration`, `AbilitySyncNeededEvent`, `SyncEventWiring` |

**Everything not listed above is internal and subject to change without notice.**

## Extension points

Consumers extend core through exactly four mechanisms:

1. **Registry registration** — providers register `AbilityDefinition` entries
   on their own mod bus via `RegisterEvent` for `AbilityRegistries.ABILITY_REGISTRY_KEY`.
2. **Handler declaration** — providers register `ActivationHandler` +
   `ChargingModel` (+ optional `ChargeAction`) via
   `ActivationHandlerRegistry.register(...)`.
3. **Eligibility rules** — policy providers register `EligibilityRule`
   instances (source-scoped) via `EligibilityChain.register(...)` on
   `AbilityCore.ELIGIBILITY` or a composed child chain.
4. **Sync events** — consumers listen for `AbilitySyncNeededEvent` on the
   NeoForge game bus and send their state payloads.
5. **Overlay providers** — providers declare an `AvailabilityProvider` per
   ability via `AvailabilityProviders.declare(...)` to feed the bar's
   cooldown/cost/availability overlay (REQ-HOT-08).

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

```groovy
repositories {
    maven {
        name = 'GitHubPackages'
        url = uri('https://maven.pkg.github.com/HexaHexes/ability-core')
        credentials {
            username = System.getenv('GITHUB_ACTOR') ?: project.findProperty('gpr.user')
            password = System.getenv('GITHUB_TOKEN') ?: project.findProperty('gpr.key')
        }
    }
}

dependencies {
    implementation 'org.bitwisemadness:ability_core:<version>'
}
```

**Secret scoping:** `GITHUB_TOKEN` must be injected scoped to the Gradle
step only (never at job level — it silently changes what `actions/checkout`
and `gh` authenticate as). The org secret `PACKAGE_READ_TOKEN` provides
cross-repo read access; GitHub returns 404 (not 401) when credentials are
missing, so an unauthenticated consumer fails with a message that looks
like a mistyped coordinate.

**Version pinning:** consumers pin the exact alpha version they were
verified against (e.g. `1.0.0-alpha.9`). Bumps are deliberate, never
implicit (see AGENTS.md "Pin the exact version").

## Leak gate

The `ApiLeakGateTest` (in `src/test/`) scans the compiled class files of all
supported packages for byte-level references to upstream mod types
(`io/redspace/ironsspellbooks/`, `io/github/manasmods/`, `net/bettercombat/`,
`net/kayn/better_spellcasting/`, `net/neoforged/fml/ModList`). Any hit fails
the build. This gate runs in CI and is the mechanical enforcement of the
"core is provider-independent" invariant (ADR-001, REQ-XREF-03).

# API surface policy

This document defines the stable, consumer-facing API of `ability-core`,
the breaking-change and deprecation policies, and the dependency coordinates
for the four consumer repositories.

## Supported packages

| Package | Stability | Purpose |
|---|---|---|
| `org.bitwisemadness.abilitycore.api` | **Stable** — public API | Data types: `AbilityDefinition`, `ActivationRequest`/`Result`/`Outcome`/`Source`, `TargetContext`, `ActivationType`, `ChargingModel`, `Affordability`, `ResourceCost`, `ResourceConsumer` |
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
   `TensuraAbilityCore.ELIGIBILITY` or a composed child chain.
4. **Sync events** — consumers listen for `AbilitySyncNeededEvent` on the
   NeoForge game bus and send their state payloads.

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

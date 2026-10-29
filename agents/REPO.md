# Repo rules - tensura-ability-core

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim - **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/tensura-ability-core` - **Mod ID:** `tensura_ability_core` - **Branch:** `main`
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.259 / JDK 21 - **Jar:** `build/libs/tensura_ability_core-<version>.jar`

Shared, provider-independent ability infrastructure for the Tensura RPG pack:
ability registration and metadata, activation request/result contracts, resource,
cooldown, targeting, eligibility and synchronisation contracts that provider and
consumer mods build on. Deliberately independent of Tensura, Iron's Spells,
Better Combat and Better Spellcasting - upstream-specific behaviour belongs in
the consuming adapters.

**Dependencies**
- Required: Minecraft, NeoForge.
- Optional: none, and none should appear - keep this repo free of upstream-mod
  dependencies so every consumer can share it. Adapters live in the consumer repos.

**Test:** `./gradlew runGameTestServer` - `TensuraAbilityCoreGameTests` (1 test). `expectedTests = 1`
in `build.gradle` must match the `@GameTest` count.

**Gotchas**
- **This repo is the API hub.** tensura-iss-integration, tensura-ability-hotbar,
  tensura-better-spellcasting-bridge and tensura-battlewills consume the published
  artifact. A breaking contract change needs the `breaking` label and a major/minor
  bump - patch releases must not move consumer-visible contracts.
- Cross-repo artifact reads need the org secret `PACKAGE_READ_TOKEN`, injected
  scoped to the Gradle step only (see AGENTS.md - GitHub 404s, not 401s, on
  unauthenticated package reads).
- Bootstrap state: no contracts exist yet - the open issues hold the design.
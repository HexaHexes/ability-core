# Repo rules — <repo name>

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim — **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/<repo-name>` · **Mod ID:** `<mod_id>` · **Branch:** `main`
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.259 / JDK 21 · **Jar:** `build/libs/<mod_id>-<version>.jar`

One-paragraph description of what this mod is and what it changes in the game.

**Dependencies**
- Required: Minecraft, NeoForge.
- Optional: list any dependency the mod works without, and how the code gates it.

**Test:** `./gradlew runGameTestServer` — `<ClassName>` (1 test). `expectedTests = 1`
in `build.gradle` must match the `@GameTest` count.

**Gotchas**
- Note anything a future edit could break: mixin targets, version-file coupling,
  tag overrides, or a dependency that is optional in the toml but hard in the code.

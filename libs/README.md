# Vendored third-party libraries

| File | Origin | License |
|---|---|---|
| `SkysLeyLines-0.5.4.121012.jar` | [Sky's Ley Lines](https://github.com/MinecraftModDevelopmentMods/SkysLeyLines) by SkyBlade (CurseForge project 1783346), build `0.5.4.121012` | LGPL-2.1 — full text in `LICENSE-SkysLeyLines-LGPL-2.1.txt` |

Both files support the **optional** Sky's Ley Lines interop (core#96):

- Used as `compileOnly` / `testImplementation` (`files('libs/...')`) exactly as
  the mod's `docs/INTEGRATION.md` prescribes — the classes are **never shaded
  or bundled** into the published artifact; at runtime the mod ships as its
  own JAR and the bridge links only when `ModList` reports it present.
- The vendored JAR is **unmodified**. It is redistributed only in this
  repository (source form of the integration), with license and attribution
  alongside, per LGPL-2.1's binary-distribution terms.
- Bump deliberately: version, `build.gradle`, the optional dependency's
  `versionRange` in `neoforge.mods.toml`, and the tested-on note in
  `docs/api-surface.md` move together.

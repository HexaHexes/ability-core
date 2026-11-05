# Compatibility matrix (REQ-DOC-03)

**Review rule: no claim without an evidence link.** Every interop, licence
and conflict entry below links the spike, DEC, ADR or issue it comes from;
a cell reading *not audited* means exactly that - no spike covers it yet.
Rows update in the same PR as any spike or ADR that changes them
(traceability rule, same as `docs/requirements.md`'s inventory).

Seeded from the seventeen closed investigation spikes (`docs/requirements.md`,
"Spike register") on 2026-11-05. Executing combination tests is **not** this
document's job - that is the test campaign (core#27).

| Component | Version | Interop mechanism | Licence posture | Conflict rules | Evidence |
|---|---|---|---|---|---|
| Minecraft / NeoForge | 1.21.1 / 21.1.259 | platform: registry sync, registry data maps, `versioned().optional()` payloads | platform terms (upstream) | n/a - platform | [core#2](https://github.com/HexaHexes/ability-core/issues/2), [core#4](https://github.com/HexaHexes/ability-core/issues/4) |
| Tensura: Reincarnated | 2.0.1.3 | datapack metadata + code adapters (execution always needs code) | ARR, closed source - structural use only ([DEC-06](https://github.com/HexaHexes/ability-core/blob/main/docs/requirements.md#conflicts-and-open-decisions)) | ownership matrix (iss#4) | [bw#2](https://github.com/HexaHexes/ability-battlewills/issues/2), [iss#3](https://github.com/HexaHexes/tensura-iss-integration/issues/3) |
| ManasCore | 4.0.0.2 | native skill registration API | GPLv3 - linkage accepted with attribution ([DEC-06](https://github.com/HexaHexes/ability-core/blob/main/docs/requirements.md#conflicts-and-open-decisions)) | ownership matrix (iss#4) | [bw#11](https://github.com/HexaHexes/ability-battlewills/issues/11), [bw#13](https://github.com/HexaHexes/ability-battlewills/issues/13) |
| Tensura RPG System | 0.1.84-shipped | optional equipping/restriction hooks (soft, never hard) | not recorded - see audit | ownership matrix (iss#4) | [iss#3](https://github.com/HexaHexes/tensura-iss-integration/issues/3) |
| Iron's Spells (ISS) | 3.16.3 (+9 schools) | events + public API (cast pipeline, mana deduction, school/equipment hooks) | addon-dependency use granted (ISS LICENSE.md, per [DEC-01](https://github.com/HexaHexes/ability-core/blob/main/docs/requirements.md#conflicts-and-open-decisions)) | ownership matrix (iss#4) | [iss#2](https://github.com/HexaHexes/tensura-iss-integration/issues/2) |
| Better Combat | 2.4.0 | events/timing via public API only | ARR - no copied code, no asset reuse ([DEC-01](https://github.com/HexaHexes/ability-core/blob/main/docs/requirements.md#conflicts-and-open-decisions)) | ownership matrix (iss#4) | [bridge#3](https://github.com/HexaHexes/tensura-better-spellcasting-bridge/issues/3) |
| Better Spellcasting | **1.2.0 - absent from the pack** (add before bridge testing, [DEC-04](https://github.com/HexaHexes/ability-core/blob/main/docs/requirements.md#conflicts-and-open-decisions): `BetterSpellcasting-1.21.1-1.2.0.jar`, CurseForge file 7571236) | events (mixins require explicit maintainer approval) | ARR - no copied code ([DEC-01](https://github.com/HexaHexes/ability-core/blob/main/docs/requirements.md#conflicts-and-open-decisions)) | ownership matrix (iss#4) | [bridge#3](https://github.com/HexaHexes/tensura-better-spellcasting-bridge/issues/3) |
| official `tensura_iron_spells` | 2.0.0.0 | overlap audited; coexist via disjoint datapack paths | not recorded - see audit | ownership matrix (iss#4) | [iss#4](https://github.com/HexaHexes/tensura-iss-integration/issues/4) |
| `tensura-iss-growth` | 1.1.0-alpha.1 | overlap audited; EP ladder kept disjoint | not recorded - see audit | ownership matrix (iss#4) | [iss#4](https://github.com/HexaHexes/tensura-iss-integration/issues/4) |
| Curios | pack-present | optional **scope-gated** hotbar bars (a missing integration disables only its own bars) | not recorded - third-party API | scope guard: never hard-required | [hotbar#90](https://github.com/HexaHexes/ability-hotbar/issues/90) |
| Wind Spellbooks (optional addon) | - | **not audited** - no spike covers it (see the spike register) | not audited | not audited | absence evidenced by [the spike register](https://github.com/HexaHexes/ability-core/blob/main/docs/requirements.md#spike-register-investigation-issues) |

Ownership/conflict rules for every row live in the
[ownership matrix](https://github.com/HexaHexes/tensura-iss-integration/issues/4)
(SPIKE-iss-3); version pins are re-verified against each repo's `README`/
`gradle.properties` at release time (see `docs/release-plan.md`).

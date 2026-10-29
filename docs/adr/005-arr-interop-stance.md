# ADR-005: All Rights Reserved upstream - interop-only posture

Status: accepted

## Context

Key upstreams carry restrictive or contradictory licences, verified this
project: Better Combat - `LICENSE` "All Rights Reserved" (MIT disclaimer
without grant) and matching mods.toml; Better Spellcasting 1.21 branch -
gradle.properties "All Rights Reserved" vs mods.toml "LGPL-2.1-or-later" with
no LICENSE file on the branch; Iron's Spells - "All Rights Reserved" **with an
explicit grant** for writing dependent addons/datapacks but no asset reuse;
Tensura: Reincarnated - "All Rights Reserved" with no grant text (structural
API use is the universal published-addon precedent); ManasCore - GPLv3;
Tensura RPG System - MIT.

## Decision

Treat **all ARR upstreams as interop-only**: integrate through events, public
fields/registries, datapack formats and runtime API calls; **never copy source
or assets** into our jars (icons are referenced by ResourceLocation at
runtime). Mixins into Better Spellcasting/Tensura require explicit maintainer
approval (org doctrine: public API only). ISS dependency-use is explicitly
permitted by its LICENSE.md. GPLv3 ManasCore linkage for `tensura-battlewills`
is accepted per ADR-010 with attribution obligations (battlewills issue #13).

## Consequences

- No shaded/forked upstream code anywhere; CI grep gates (leak test) back this
  in core issues #12/#20.
- Licence contradictions are resolved as stance, not as legal conclusions -
  the record links the evidence, not a lawyer's opinion.
- Attribution lives in each repo's mandatory NOTICE.

Links: bridge issue #3 findings (DEC-01), iss issues #2 and #3 findings, bw
issues #11 (DEC-06) and #13.

# Release plan (REQ-DOC-04)

How each of the five repositories releases, what CI owns, and the
core-before-consumers sequence. Versioning/stability policy:
`docs/api-surface.md`; wire-version discipline: `docs/sync.md`.

All five repos share the same template-synced workflow set (`ci`,
`codeql`, `dependency-review`, `dependency-submission`, `release`,
`publish`, `curseforge`, `modrinth`, `version-bump`,
`version-bump-post-merge`) - verified identical file lists on 2026-11-05 -
so the flow below is the flow everywhere; per-repo differences are called
out in the checklists.

## The release train

1. **PRs are gated, never red**: `junit` (build + SpotBugs/Checkstyle/
   spotless + suite), `gametest` (boot-level), `static`, plus CodeQL and
   dependency checks. Do not merge on a red or pending check.
2. **PRs never change `mod_version`** - the alpha/beta slot is CI/CD-owned;
   the `version-bump` PR check enforces it (template#81).
3. **Merge → `version-bump-post-merge.yml`** creates the version-slot
   commit (`X.Y.Z-alpha.NN` - always two digits, octal-safe parsing,
   template#99).
4. **`release.yml`** sees the unreleased version and creates the GitHub
   release (alpha/beta as *pre-releases* - see ALPHA_RELEASES below).
5. **On release published:**
   - `publish.yml` → **GitHub Packages Maven** - the artifact consumers
     resolve (reads require `PACKAGE_READ_TOKEN`; see `docs/api-surface.md`
     for coordinates and pinning);
   - `curseforge.yml` → **CurseForge, including alphas** (owner ruling
     2026-11-03); dispatched by `release.yml` as well, because
     `GITHUB_TOKEN`-created releases do not cascade workflow runs;
   - `modrinth.yml` → **Modrinth, GitHub-only for alphas** (the channel
     policy); a repo with neither Modrinth secret skips with a notice
     (treated as "not listed yet"), a repo with only one fails loudly.
6. **Release-time verification** - see the checklist below.

## Core before consumers (sequencing)

Core is the published foundation; consumers pin it **exactly**
(`docs/api-surface.md`, "Version pinning"). The sequence when core changes:

1. Merge + release core (steps 1-5 above) - the artifact must exist in
   GitHub Packages first.
2. Consumers bump their `ability_core` pin **in their own PR**, green, then
   follow the same train. Verified pins as of 2026-11-05:
   `ability-hotbar` → `alpha.15`, `tensura-iss-integration` → `alpha.9`,
   `tensura-battlewills` → `alpha.9` (each repo's `build.gradle` is the
   source of truth).
3. **Never deploy mismatched sides**: a wire codec change bumps the channel
   version and both sides ship together - a skew disconnects players rather
   than misparsing (`docs/sync.md`).

## Per-repo checklists

### `ability-core` (org.bitwisemadness.abilitycore:ability_core)
- [ ] CI green on the PR; no `mod_version` change in the PR.
- [ ] If a registered codec changed: channel version bumped **and** consumer
      pins listed for step 2 of the sequence above (`docs/sync.md`).
- [ ] After merge: version-slot commit → GitHub release → Maven publish.
- [ ] Release-time verification: review `docs/risk-register.md` (landed with
      core#83) and re-check `docs/config.md`'s manual invalid-value step.
- [ ] **First stable cut — arm-day check (core#20 AC3):** the CI `version-bump`
      job's breaking→major/minor rule is *inert* until a stable `v*` tag exists
      ("No stable tag exists yet" / "applies to the next clean bump" — its own
      output while alpha-only). On the first `v1.0.0`: merge a `breaking`-labelled
      PR, attempt a patch-only bump, and confirm CI goes red demanding major/minor,
      then confirm the clean bump passes. Demo recorded on core#20.

### `ability-hotbar` (org.bitwisemadness.abilityhotbar:ability_hotbar)
- [ ] CI green; no `mod_version` change in the PR.
- [ ] Core pin bumped first if this release needs newer core.
- [ ] Manual checklists current before cutting the release:
      `docs/manual-input-checklist.md`, `docs/menu-checklist.md`,
      `docs/render-checklist.md` (they gate hotbar#27/#83/#86 closures).

### `tensura-iss-integration` (org.bitwisemadness.tensuraissintegration:tensura_iss_integration)
- [ ] CI green; no `mod_version` change in the PR.
- [ ] Core pin bumped first if needed; ISS upstream-version compatibility
      re-checked (the integration's whole point).

### `tensura-better-spellcasting-bridge` (org.bitwisemadness.tensurabetterspellcastingbridge:tensura_better_spellcasting_bridge)
- [ ] CI green; no `mod_version` change in the PR.
- [ ] Core/ISS pins bumped first if needed; single-charge hooks re-verified
      against the current contract (ADR-002's mapping table).

### `ability-tensura` (org.bitwisemadness.abilitytensura:ability_tensura)
- [ ] CI green; no `mod_version` change in the PR.
- [ ] Core pin bumped first if needed.

## ALPHA_RELEASES (verified 2026-11-05)

Mechanism: `release.yml` exports the repository variable `ALPHA_RELEASES`
into a shell guard - **unset means enabled** (`${ALPHA_RELEASES:-true}`),
`"false"` opts out (the release is skipped with a log line, never a
failure). Pre-releases (alpha/beta) only publish as GitHub releases when
enabled; the downstream publish workflows follow as above.

| Repo | Variable | Alpha releases |
|---|---|---|
| `ability-core` | unset | enabled (default) |
| `ability-hotbar` | `true` (explicit) | enabled |
| `tensura-iss-integration` | unset | enabled (default) |
| `tensura-better-spellcasting-bridge` | unset | enabled (default) |
| `tensura-battlewills` | `true` (explicit) | enabled |

(Originating issue #30 recorded "current: unset/off" before the template's
`:-true` default landed; the table above is the verified state - re-verify
with `gh variable list -R <repo>` when this plan is reviewed.)

Links: issue #30 (originating contract), issue #29 (risk register review
step), `docs/api-surface.md`, `docs/sync.md`, `docs/config.md`.

# Agent rules and conventions

Org-wide rules for every HexaHexes mod repo. This file is maintained in the
[`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
repo and pulled into each repo **verbatim** - never hand-edit it here. Rule
changes are made in the template first (own branch + squash PR), then the file
is pulled into this repo as its own PR.

Repo-specific rules - identity, description, dependencies, test commands,
gotchas and sanctioned exceptions - live in [`agents/REPO.md`](REPO.md), the
only agent-rules file edited inside a repo.

---

## Before you start (do this first)

Before starting any new development in this repo:

1. **Open issues for the work.** Every piece of work begins as an issue - or
   several, for complex tasks - detailing what needs to be done; the PR
   references them. No tracking issue, no work.
2. **Check rule freshness.** Compare this repo's `agents/AGENTS.md` against the
   template's and reconcile any drift before touching code - rules changed in
   the template must have been pulled here first.
3. `git status` — confirm what uncommitted work exists. Never disturb or sweep it.
4. `git pull --ff-only` — sync with the remote. A fast-forward is the normal case,
   because history on the default branch is never rewritten.
5. If the pull would **not** fast-forward (the branch has diverged), or if it would
   collide with local uncommitted work, **stop and ask** rather than merging, rebasing
   or stashing on your own.

**Remote:** `github.com/HexaHexes/<name>` (default branch `main`). Direct pushes to
the default branch are blocked by branch rulesets - land changes through a PR. Push
anything only after checks are green and the user has given the go.

**Pulling rule updates:** when the template's `agents/AGENTS.md` changes, pull the
file into this repo verbatim as its own PR - never re-type or patch it by hand.

## Scope doctrine

- **One mod + bundled datapack.** No KubeJS scripts.
- **Data-driven over code** — prefer JSON/datapack content to Java when both work.
- **Public API only, no mixins** in the colony-gear-style addon mods. Sanctioned
  exception: `ds-tensura-fix` (tensura-dragon-skin-rebalance), a mixin fix mod by design.

## Verification

- Verify with the in-repo GameTest run (`./gradlew runGameTestServer`). Repos without
  GameTests verify with `./gradlew build`. Never verify against a live CurseForge
  instance.
- Keep `expectedTests` in `build.gradle` in sync with the `@GameTest` methods.
- **Client-render features cannot be proven headless.** The honest scope is
  "build green + the user's in-game check" - say so rather than overclaiming.

## Build environment

- JDK 21 (`JAVA_HOME` -> JDK 21); the toolchain targets Java 21.
- The Gradle wrapper is committed. `./gradlew build` compiles,
  `./gradlew runGameTestServer` runs the GameTest server.
- **Dependencies resolve through Maven, never local folders.** Compile-only mods use
  CurseMaven coordinates (`curse.maven:<slug>-<projectId>:<fileId>`, repository
  `https://cursemaven.com`); Modrinth-exclusive mods use the official Modrinth maven
  (`maven.modrinth:<project-slug>:<version>`, repository
  `https://api.modrinth.com/maven`). Runtime mod closures for GameTest runs live in a
  dedicated Gradle configuration synced into the run's `mods/` folder. Gitignored
  `libs/` and `testmods/` staging folders are retired: CI must resolve every
  dependency from a clean checkout. Pin the exact version the repo was verified
  against; bump pins deliberately, never implicitly.
- **Every repo publishes its artifact to GitHub Packages** (`https://maven.pkg.github.com/HexaHexes/<repo>`)
  for alpha, beta and stable releases - never publish to or consume from a
  gitignored local jar. Watch three things here:
  - **Reads always require auth, and GitHub answers 404 (not 401) when it is missing**,
    so an unauthenticated consumer fails with a message that looks like a mistyped
    artifact coordinate rather than a credential problem.
  - **The workflow `GITHUB_TOKEN` can publish a package owned by its own repo but
    cannot read a package owned by a different repository.** Consumers use the org
    secret `PACKAGE_READ_TOKEN` (classic token, `read:packages`). Declare the
    repository in `build.gradle` with credentials reading `System.getenv('GITHUB_ACTOR')`
    / `System.getenv('GITHUB_TOKEN')`, falling back to the `gpr.user` / `gpr.key`
    Gradle properties for local builds.
  - Inject the secret **scoped to the Gradle step only**
    (`env: GITHUB_TOKEN: ${{ secrets.PACKAGE_READ_TOKEN }}`), never at job level -
    job-level injection silently changes what `actions/checkout` and any `gh` CLI
    step authenticates as.
- On `run/` file-lock errors, kill any stale `java` process and retry.

## PowerShell quirks (this org is developed on Windows)

- A `git push` "error" in PowerShell is usually stderr passthrough — success is the
  `hash..hash  branch -> branch` line, not the absence of red text.
- `New-Object Type(args)` breaks on arithmetic — use `::new()` or a parenthesized
  argument.
- **Never write files with `Set-Content -Encoding UTF8`** — PowerShell 5.1 writes a
  UTF-8 BOM that breaks Gradle's parser and YAML. Use
  `[System.IO.File]::WriteAllText($path, $text)` (no BOM). Re-check for BOMs after
  any script-written edit.
- **A fresh `git init` on Windows records new files as `100644`** — the executable bit
  is invisible to git there, so a committed `gradlew` reaches CI as non-executable and
  every job dies with `./gradlew: Permission denied` before Gradle starts. Before a
  repo's first commit, run `git update-index --chmod=+x gradlew` and confirm with
  `git ls-files -s gradlew` (expect `100755`).
- There are no repo-owned `.ps1` scripts any more. One-off inspection commands are
  fine; anything that *generates shipped content* belongs in the build, not in a
  script.

## Git & release

- Remote is SSH: `git@github.com:HexaHexes/<name>.git`.
- Push only after checks are green **and** the user has given the go.
- Never sweep the user's uncommitted work into a commit — commit only what is intended.
- Commit subjects: concise, imperative.
- **Version bump on finish:** always bump `mod_version` in `gradle.properties`
  when a new feature or bugfix is finished.
- **One feature or bugfix per branch:** develop each on its own feature branch and
  merge it into the default branch through a pull request with a **squash merge**.
  The PR body must detail the work done, following `.github/pull_request_template.md`.

## House style

- **No icons** unless the user explicitly asks. Icons are being removed across all repos.
- **No emojis, ever.** Prohibited in all code, docs, configs, and commit messages.
- READMEs are **player-facing only**: pitch, mechanics in plain terms, in-game usage,
  required/optional dependencies, credits, license. No build internals, no class names.
- **AI disclosure in every README.** Every `README.md` ends with the org's
  AI-assistance disclosure as its final section, in the canonical wording -
  identical in every repo, no variants:

  ```markdown
  ## AI disclosure

  Parts of this project were written with the assistance of AI coding tools.
  AI-assisted code, data and documentation ship only after human review by the
  maintainers.
  ```
- All repos are **LGPL v2.1**, copyright Bitwise Madness. `NOTICE` / attribution files
  are mandatory and must never be deleted.
- Deploy to the Tensura RPG instance **only** when the user asks.

## Naming

- **Repo and folder:** lowercase, hyphenated (e.g. `tensura-colony-gear`).
- **`mod_id`:** lowercase with underscores (e.g. `tensura_colony_gear`). Minecraft
  requires `[a-z][a-z0-9_]{1,63}`.
- **Jar / `archivesName`:** follows `mod_id` by default.
- **Java base package:** `org.bitwisemadness.<project-name>` - repo name lowercased,
  separators stripped (e.g. `ds-tensura-fix` -> `org.bitwisemadness.dstensurafix`).
  The Maven `group` in `build.gradle` matches (`org.bitwisemadness`).
- Keep repo name, folder name, `mod_id`, and the Java package aligned; the rename
  checklist in `README.md` walks the whole set.
- **This template is canonical for org-wide rules.** Rules change here first;
  consuming repos never hand-edit their `agents/AGENTS.md` - they pull the file
  verbatim (see Before you start).

## CI & release model

- `ci.yml` runs three checks that back the branch protection rulesets: **junit**
  (build + the JUnit suite, asserted non-vacuous), **gametest** (`runGameTestServer`,
  graded on the `All N required tests passed` log line - the exit code is not a
  reliable signal) and **version-bump** (a beta or stable bump must change the
  version file and nothing else; an `-alpha` bump may bundle work; a PR labelled
  `breaking` forces the next clean bump to raise the major or minor version).
- **Branch protection:** the default branch is guarded by two repository rulesets -
  `protect-main` (PR-only, squash merge only, strict `junit`/`gametest`/`version-bump`
  status checks, no force-pushes, no branch deletion) and
  `require-review-from-write-access` (one approving review; the listed org owners
  bypass). Direct pushes to the default branch are rejected by GitHub.
- `version-bump.yml` opens an isolated version-bump PR (only the version file changes).
- `release.yml` publishes a GitHub release for `v<mod_version>` on a push to the default
  branch when that release does not exist yet, attaching the jar the build produced. The
  jar name is read from `mod_id`, so a rename needs no edit here. Pre-releases are
  opt-in: an `-alpha`/`-beta` version publishes only when the repository variable
  `ALPHA_RELEASES` is `true`, otherwise it is skipped with a log line rather than a
  failure. Promoted from `tensura-iaf-growth`, the org's reference implementation, and
  its release notes are player-facing - rewrite them when promoting the file into a mod.
- `publish.yml` publishes the jar as a Maven artifact to the org's GitHub Packages
  registry whenever a GitHub release is published, via the `maven-publish` block in
  `build.gradle` (artifactId is `mod_id`; pom metadata comes from `gradle.properties`;
  the block's URL names the repository it lives in - the rename checklist covers it).
  Alpha, beta and stable releases all publish: pre-releases are gated upstream by
  `release.yml`, so anything that reaches "release published" is meant to be
  consumable. `workflow_dispatch` lets a maintainer republish a version after a
  packaging fix without cutting a new release.
- `modrinth.yml` publishes the same jar to Modrinth whenever a GitHub release is
  published (or on demand via `workflow_dispatch` with a tag), mirroring the
  CurseForge design that lives in `tiered-echoes`: alphas stay GitHub-only, betas
  and stables publish, and the asset and changelog are read back from the release
  rather than the event payload. It skips with a notice until the repo has a
  Modrinth listing (`MODRINTH_PROJECT_ID` + `MODRINTH_TOKEN` secrets), and fails
  loudly when only one of the two is set - half-configured is a mistake,
  unconfigured is a deferral. Because GitHub refuses to cascade workflow runs
  started by the default `GITHUB_TOKEN`, `release.yml` dispatches `publish.yml`,
  `modrinth.yml` and `curseforge.yml` (whichever a repo ships) directly after
  tagging - the `release: published` trigger only covers releases made by hand.
  When promoting the file into a repo, edit the two
  env lines at the top: the Minecraft version and the loader names (Modrinth's
  lowercase forms: `neoforge`, `forge`).
- CurseForge publishing (`curseforge.yml`) is **deferred** until the mod has a CurseForge
  listing and the `CURSEFORGE_PROJECT_ID` / `CURSEFORGE_API_KEY` secrets. Do not add it
  speculatively.

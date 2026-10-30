## Changeset

<!-- One logical change per PR. Split unrelated work into separate PRs. -->

## Checklist

- [ ] This PR contains **one** logical changeset
- [ ] `./gradlew build` passes locally (compiles)
- [ ] `./gradlew runGameTestServer` passes locally (assert on the
      `required tests passed` line - the exit code is not reliable)
- [ ] Change is documented; README updated if user-facing
- [ ] This PR adds a feature, or changes one `README.md` already describes,
      and updates `README.md` in this PR
- [ ] No logs, `run/`, or `crash-reports/` committed

## Breaking change

<!-- Tick only if this PR breaks backwards compatibility: datapack schema,
     item/NBT format, or public API. CI uses this label to require that the
     next beta or stable version bump raises major or minor, never patch. -->

- [ ] This change is **breaking** - apply the `breaking` label before merge

## Version bump

<!-- Ruling: feature changes and bug fixes bump the -alpha.N channel in this
     PR; template-parity pulls from neoforge-mod-template never bump. Complete
     this section if and only if gradle.properties is changing. -->

- [ ] This PR bumps `mod_version` **and nothing else**
- [ ] New version is a clean bump (e.g. `1.0.0`) - releases as stable
- [ ] New version has a `-beta`/`-rc` suffix - releases as beta
- [ ] New version has an `-alpha` suffix - releases as alpha; other
      changes in this PR are permitted

<!-- A clean or non-alpha bump combined with any other change is rejected
     by the version-bump check. Alphas may bundle work. -->

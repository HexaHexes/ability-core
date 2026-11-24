#!/bin/bash
# Resolve the newest stable release tag for the semver floor (issue #104) and
# the breaking-magnitude rule (ci.yml) - one implementation so the three
# callers cannot drift (issue #109).
#
# Stable tags come in two shapes and both must work:
#   legacy       v1.2.3
#   loader line  neoforge-1.21.1-1.2.3   (issue #107 / #108)
# The loader token and the Minecraft-version token never carry a hyphen, so
# the version starts after the second hyphen on a loader-line tag; a tag that
# is not a clean X.Y.Z afterwards (pre-releases, odd shapes) is skipped.
#
# Emits "<tag><TAB><X.Y.Z>" for the newest by version, or nothing at all when
# no stable tag exists yet. Callers need both fields: the original tag for
# `git log -1 --format=%cI "$BASELINE"` and the bare version for the semver
# math (the old `${BASELINE#v}` is a no-op on a loader-line tag).
set -uo pipefail

best_tag=""
best_ver=""
while IFS= read -r tag; do
  [ -n "$tag" ] || continue
  ver="${tag#v}"
  case "$ver" in
    *-*-*) ver="${ver#*-*-}" ;;
  esac
  printf '%s\n' "$ver" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' || continue
  if [ -z "$best_ver" ] || [ "$(printf '%s\n%s\n' "$best_ver" "$ver" | sort -V | tail -1)" = "$ver" ]; then
    best_tag="$tag"
    best_ver="$ver"
  fi
done < <(git tag --list 'v*' '*-*')

if [ -n "$best_tag" ]; then
  printf '%s\t%s\n' "$best_tag" "$best_ver"
fi

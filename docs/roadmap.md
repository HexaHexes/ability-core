# Roadmap - tensura-ability-core

Generated from and kept in sync with this repository's issues. Phases are the
ecosystem-wide plan; the authoritative status is always the issue list.

Cross-repository documents (full requirement inventory, dependency map, ADR log,
compatibility matrix, risk register) live in this repository's `docs/` folder -
this repo is the shared dependency every other ecosystem repo consumes.

## Phase 0 - Organisation and repository foundation

- [x] Repositories created from `neoforge-mod-template` (bootstrap PR #1 merged)
- [x] Issue forms (feature / bug / investigation), labels, branch rulesets applied

## Phase 1 - Requirements and feasibility

- [x] Requirements inventory (`docs/requirements.md`)
- [ ] SPIKE-core-1 - core contract surface (#2)
- [ ] SPIKE-cfg-1 - configuration mechanisms (#3)
- [ ] SPIKE-cfg-2 - sync patterns (#4)
- [ ] Cross-repository dependency map + ADR log (after SPIKE-core-1)
- [ ] Compatibility matrix and risk register (after upstream spikes)

## Phase 2 - Shared API and contracts

Blocked on SPIKE-core-1. One issue per REQ-CORE-01..09, created when the spike
closes.

## Phases 3-6

Owned by the consumer repositories; see their roadmaps:
`tensura-iss-integration`, `tensura-ability-hotbar`,
`tensura-better-spellcasting-bridge`, `tensura-battlewills`.

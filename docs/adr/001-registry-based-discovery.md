# ADR-001: Registry-based ability discovery

Status: accepted

## Context

Consumers (hotbar, bridge) must enumerate abilities from providers (Tensura,
ISS, battlewills) that may be absent, without branching on mod presence, and
definitions should reach clients without hand-rolled packets. Candidates: a
core-owned NeoForge registry, a static service-locator map, or per-consumer
scans. Additionally, the brief requires datapack-driven definitions
(REQ-BW-07).

## Decision

- Use a **built-in NeoForge registry** (`NewRegistryEvent` + `RegistryBuilder`)
  holding `AbilityDefinition` records; providers register at registry time with
  provider-namespaced ids (issue #12).
- Datapack extension is served by **data maps attached to built-in entries**
  (`DataMapType`), not by making the registry itself a datapack registry -
  verified against NeoForge 1.21.1 source (`DataPackRegistryEvent`): datapack
  registries are JSON-only (cannot hold code-registered provider entries) and
  a network codec forces clients to have the registry to join.
- Absence semantics fall out of the model: provider gone -> ids absent ->
  saved references resolve to "preserved but inert".

## Consequences

- Definition sync reuses NeoForge's registry id-mapping sync; no custom
  definition packet.
- `data/<ns>/...` overlays land via data maps (`/reload`), one catalog, no
  dual-merge lookup.
- A future migration to datapack registries would be a breaking contract
  change (`breaking` label + major bump).

Links: issues #12 (implementation), #14 (absence semantics), #2 (originating
spike), feasibility comment on #12.

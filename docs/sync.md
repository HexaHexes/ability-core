# Sync contract and payload inventory (REQ-CORE-08)

Living document for the ability-state transport contract, per **ADR-003**
(sync split) and **ADR-011** (multi-hotbar state model). Core owns the
channel discipline and the lifecycle signal; consumers own their payloads and
answer the signal with state.

## Channel discipline

One registrar per mod: `event.registrar(modid).versioned(N).optional()`.
Core's channel is at version **3** (1: baseline; 2: target hint, core#69;
3: mode context, issue #113).

- `optional()` tolerates channel **absence** (one side lacks the mod) but
  never **version skew** - a mismatched version disconnects at negotiation
  (`NetworkComponentNegotiator`), it never misparses.
- Client→server payloads are **requests only**; the server answers state
  mutations with a snapshot (accepted: the mutated state; rejected: the
  current state - ADR-003's "rejection re-sends the snapshot"). Core itself
  sends no game data: it fires `AbilitySyncNeededEvent`
  (`SyncEventWiring`: LOGIN, RESPAWN, DIMENSION_CHANGE) and consumers answer
  it with a full snapshot. The one exception is the **solicited** mastery
  query answer (`MasteryQueryResponse`, REQ-CORE-12): a reply to an explicit
  request, never a push - core still originates no state on its own.

### Version-bump checklist

Adding a **new** payload type is additive and does not bump `N`: an
older peer never sends it (its UI does not exist), and a peer that
predates the type rejects it at the payload layer - ordinary version
skew, the same as any consumer payload. Only changing a **registered**
codec bumps the channel.

When a registered codec changes:

1. Change the codec **and** the `versioned("N")` string in the same PR.
2. Label the PR `breaking` - CI's version-bump check forces a major/minor
   version (never a patch).
3. Ship both sides together: bump every consumer's exact version pin (see
   `docs/api-surface.md`, "Version pinning") in the same release train.
4. Never deploy mismatched sides - a skew disconnects players instead of
   degrading; the bump is cheap, the disconnect is not.

## Payload inventory

| Payload | Direction | Answered/fired when | Owner | Size notes |
|---|---|---|---|---|
| `BarStateSnapshotPayload` | playToClient | `AbilitySyncNeededEvent` (login/respawn/dimension) **and** after every assignment/selection/switch request - accepted *or* rejected | `tensura-ability-hotbar` | `BarState` codec: schema version + bar ids + assignments (max 18 slots/bar) + selection; a few hundred bytes |
| `SelectSlotPayload` | playToServer | Ctrl+wheel slot intent | `tensura-ability-hotbar` | one var-int |
| `SwitchBarPayload` | playToServer | Alt+wheel / Alt+digit bar intent | `tensura-ability-hotbar` | one resource location |
| `AssignAbilityPayload` | playToServer | menu assign / replace / clear | `tensura-ability-hotbar` | slot + ability id + bar id |
| `ActivateAbilityPayload` | playToServer | slot activation (Ctrl+digit / G), carrying the optional target hint and the slot's mode | **core** | id + source + slot + stamp + hint + mode; ~60-100 B typical |
| `MasteryQueryRequest` | playToServer | an explicit UI request for one player's mastery snapshot (REQ-CORE-12) | **core** | one UUID string; the answer below is its only response |
| `MasteryQueryResponse` | playToClient | the answer to `MasteryQueryRequest` - solicited only, never a push | **core** | echoed UUID + one entry per stored target (kind, id, xp, level), sorted; distinct targets only, far under the wire bound |
| delta payloads | - | **deferred** | - | see amendment below |

### Serverbound budget

Serverbound requests are intent-only and must encode **under 32 KiB**.
`SyncContractTest.activatePayloadStaysWithinTheServerboundBudget` enforces
this for core's payload with a generous worst case (a 4 000-character id -
far past any real `ResourceLocation`); the transport layer's frame limits
bound anything beyond. Requests carry no game state: an id, an enum name, an
int, a long, and an optional target hint.

### Amendment: "deltas on mutation" -> snapshot echo

ADR-003 recorded the spike's recommendation ("full snapshots on lifecycle
boundaries and deltas on mutation"). The shipped design answers **every**
mutation with a full snapshot echo instead - ADR-011's optimistic-switch
flow (accept echoes the mutated state, rejection reverts the optimistic
render). The state is a few hundred bytes, so a delta protocol buys nothing
today; delta payloads stay **deferred** until state size warrants them.
Recorded on core#19 (2026-11-04).

## Manual checks (documented procedure)

1. **Login**: join a server - expect one snapshot shortly after spawn; the
   bar layout renders from it with no empty flash.
2. **Respawn**: die and respawn - expect a fresh snapshot; layout correct
   without reopening anything.
3. **Dimension change**: travel to another dimension - expect another
   snapshot; layout correct on arrival.
4. **Mutation echo**: assign an ability in the menu - expect an echo; send a
   malformed request (modified client) - expect the *current* state echoed
   back and the client view unchanged.

Expected at every step: the client's bar view matches the server's; no
relog, reload or menu reopen is ever needed to converge.

Dedicated-server boot and vanilla-mod-absent connection rows are deferred to
the combination matrix (core#27), as recorded in `SyncContractTest`'s
javadoc.

Links: ADR-003, ADR-011, `docs/api-surface.md` (version policy), core issue
#19 (originating contract).

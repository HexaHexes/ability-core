# Ability Core

Ability Core is the shared foundation of the Tensura RPG pack's ability
mods. It defines how an ability is described, discovered, activated and paid
for, independent of where the ability comes from.

## What it does

*(Design phase, no release yet. The open issues on this repository hold the
full plan, research tables and open design decisions.)*

- **Ability contracts** - registration, metadata, categories and identifiers
  for abilities supplied by other mods.
- Activation is a single request/result path for using an ability, with
  targeting context, cooldown and resource-cost hooks.
- **Eligibility and restrictions** - server-authoritative checks that an
  ability may be used at all, before anything is consumed.
- Synchronisation sends clients what they need to draw and use abilities, and
  nothing they are allowed to decide for themselves.

## Configuration

Core ships two small config files: `ability_core-server.toml`
(`diagnostics.refusalLog` - how eligibility refusals reach the debug log) and
`ability_core-client.toml` (`diagnostics.playerNotices` - whether your client
surfaces notices). Server values are authoritative and synced; client values
are display preferences only.

Hand-edited problems never bite silently: an invalid or unknown/obsolete key
is logged at **WARN naming the key**, the game keeps running on the corrected
value, and core never rewrites or deletes your file - annotations and unused
keys survive. The full contract (precedence, diagnostics, migration) is in
[`docs/config.md`](docs/config.md).

## Dependencies
| | Mod | Why |
|---|---|---|
| Required | Minecraft / NeoForge | 1.21.1 / 21.1.259 |

Ability Core is a library. The pack's ability mods (the hotbar, the
integrations, the battlewills) depend on it, not the other way round.

## Credits
- Built on [NeoForge](https://neoforged.net/).

## License

GNU Lesser General Public License v2.1 - see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.
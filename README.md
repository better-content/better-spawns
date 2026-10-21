# Better Spawns (Forge 1.20.1, Kotlin)

## Native fixture verification

Each Gradle invocation selects a fresh nonshipping `build/gametest/<uuid>` directory in the actual
ForgeGradle GameTest RunConfig; structure copies and native log readers use that same directory.
Ordinary developer `run`, visual/data roots and prior fixtures are never reset, relocated or merged.
`verifyFast` checks isolated-directory rejection, absent-only native settings preparation,
preservation and synthetic ERROR/FATAL rejection. `verifyFull` prepares defaults-only
`server.properties` there when absent and rejects ERROR/FATAL in both actual latest/debug logs
even when tests and process exit pass. Retain complete build trees/evidence across handoffs.

## Playtest logging

`bc.playtest.better_spawns.selection` records stable validation-rejection codes and distinguishes
committed onboarding/reselection from actual spectator release or deferred release. It never logs
client packet fields, freeform rejection text, class/purchase contents, player names or spawn
coordinates. Native player UUID and current dimension/game tick correlate validation, commit and
release for simultaneous selections without publishing packet contents or an extra session ID.
This bounded causal UUID is intentional. Native validation, site-commit-before-grants and release
ordering are unchanged.
DEBUG/TRACE share 120/minute (burst 20), 256 keys and 2,048-character records, with 30-second
summaries. Startup INFO reports loaded version/mode/limits and World Management presence;
unavailable source identity is `UNKNOWN`. Stop/disconnect clears transient keys without
rearming process allowances. Pure emitter/contracts supplement the native selection gates;
no GUI or inventory visual acceptance is implied.

## Scope and authority

This repository owns its mod-specific behavior and authoring inputs. Read [local instructions](AGENTS.md)
and the [shared documentation/policy index](../../better-content-modpack/docs/README.md).


A Forge mod that forces new players to pick a class before starting:
- On first join, player is put into spectator mode.
- Client is prompted to open class menu with `K`.
- Class menu shows title, blurb, and description.
- Players lock a class and lock a respawn point separately, then press `Begin`.
- Beginning play and later respawns use the player's locked respawn point.
- Daylight pauses while every connected player is spectating and resumes when any player enters another game mode.
- Kits are JSON-defined in `config/better_spawns/kits.json`.
- Alternatively, `config/better_spawns/embark.json` can switch onboarding to an embark-style point-buy item pool.
- Respawning returns the player to that saved starting location with the scripted sound and particle FX.
- Onboarding and every respawn ensure `better_spawns:respawn_marker` beneath the stored feet position.
  The block inherits vanilla crying obsidian's appearance, light, hardness and ambient particles,
  drops itself with a diamond-or-better pickaxe, and has no recipe. Better Compat Fixes excludes
  only this exact block from the pinned RBP falling-operation admission path; vanilla crying
  obsidian retains normal physics. Existing saves upgrade lazily at the player's stored point,
  including an already safe legacy site; there is no global crying-obsidian scan.

## Admin commands

- `/better_spawns resetrespawn <targets>` clears stored permanent respawn points for online players.

## Kit slot targeting

Each item entry can include an optional `slot` field:
- `inventory` (or omitted): normal inventory insertion.
- `offhand`: attempts to equip directly into the offhand slot.
- `armor:head`, `armor:chest`, `armor:legs`, `armor:feet`: attempts to equip directly into armor slots.
- `curio:<identifier>`: attempts to equip into the first free Curios slot with that identifier (example: `curio:charm`, `curio:ring`).

If a requested slot is missing or full, the item falls back to player inventory.

## Embark point-buy mode

`config/better_spawns/embark.json` controls the selection mode:
- `"mode": "class"` keeps the existing fixed class selector.
- `"mode": "embark_points"` replaces classes with a point-buy supply screen.
- `"pointQuota"` sets how many points each player can spend.
- `"items"` defines the purchasable pool.

Each embark item supports:
- `id`: unique purchase id.
- `title`: display name.
- `category`: optional grouping label shown in the supply row.
- `blurb`: short display text.
- `item`: item id with optional NBT, using the same syntax as kit items.
- `count`: stack count granted per purchase.
- `cost`: points spent per purchase.
- `maxPurchases`: per-player purchase cap for that entry.
- `slot`: optional target slot using the same values as kit slot targeting.

## Dependencies

Curios is required and loaded as a Forge dependency.

## Release install

Use the jar produced at `build/libs/better-spawns-<version>.jar`.

- Server: install Better Spawns, Kotlin for Forge, and Curios in the server `mods/` folder.
- Client: install the same mod set and matching Forge `47.4.x` on Minecraft `1.20.1`.

Project URLs:
- Repository: `https://github.com/better-content/better-spawns`
- Issue tracker: `https://github.com/better-content/better-spawns/issues`

## Test commands

Use Java 17 for all commands.

### Verification
```bash
./gradlew verifyFast
./gradlew verifyFull
```

`verifyFast` runs the JVM and coverage lane plus the production refmap gate. `verifyFull` adds
headless Forge GameTests. The refmap gate reads the final reobfuscated JAR and requires the generated
`ServerLevelMixin` production mappings for both `tickTime` and `setDayTime`, in default and `searge`
environments. `stageRuntimeJar` also depends on this gate before copying the JAR. The Java annotation
processor's refmap/mapping sidecars are declared Gradle outputs; non-incremental compilation of the
single Java mixin recreates them if they disappear. No shipped or historical refmap is used.

### Local client
```bash
./run-client
```

`run-client` is a small helper that runs `gradle runClient` for convenience.

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).

## Canonical identity

- Repository and Gradle project: `better-spawns`
- Mod ID and resource namespace: `better_spawns`
- Maven group: `com.bettercontent`
- Runtime artifact: `build/libs/better-spawns-<version>.jar`

The canonical identity is a clean break. Legacy mod IDs, resource namespaces, configuration paths, commands, network channels, and saved-data keys are not migrated or aliased.

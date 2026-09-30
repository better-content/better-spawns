# Better Spawns (Forge 1.20.1, Kotlin)

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

`verifyFast` runs the JVM and coverage lane. `verifyFull` adds the headless Forge GameTest pass.

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

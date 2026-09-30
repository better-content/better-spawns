# Changelog

## Unreleased

- Paused daylight while every connected player is in spectator mode, in both singleplayer and multiplayer.
- Standardized the repository, artifact, package, and mod namespace under the canonical Better Content identity; this is a clean break without legacy aliases or migration.
- Replaced server-wide respawn hub voting with per-player permanent respawn selection at class pick time.

## 1.0.0 - 2026-04-07

- Initial public release of Better Spawns for Forge 1.20.1.
- Added forced first-join class selection with spectator gating.
- Added JSON-defined class kits with targeted item slot placement.
- Added Curios-backed `curio:<identifier>` slot support with inventory fallback.
- Added respawn hub management, voting, and scripted hub FX.
- Added unit tests for kit parsing and slot validation.
- Added a Forge GameTest covering datapack kit loading.

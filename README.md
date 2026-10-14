# Mob Spawn & Loot Rates (Fabric 1.21.11)

Change how many mobs spawn and how much loot they drop, in-game, through a chest-style GUI.

- **Server-side GUI:** it's a normal 9x6 chest menu, so players on a server **don't need the mod installed**. Install it on the server only, or in your own client for singleplayer.
- **Operators only:** the command and the GUI need permission level 2 (the same level as vanilla `/loot`). If someone is de-opped while the GUI is open, it closes.
- Settings are saved to `config/mobspawnrates.json` as soon as you change them.

## Commands

| Command | What it does |
|---|---|
| `/loot gui` | Opens the rates GUI |
| `/loot reload` | Reloads `config/mobspawnrates.json` from disk |

Vanilla `/loot spawn|give|insert|replace` still works. The mod only adds the `gui` and `reload` subcommands.

## Using the GUI

**Mob list** (every mob, sorted by name, across several pages)
- Click a spawn egg to edit that mob. Mobs with custom rates have an enchantment glint.
- **Nether star:** global multipliers that apply to every mob.
- **Arrows:** previous and next page. **TNT:** shift-click to reset everything. **Barrier:** close.

**Edit page**
- The top row sets the **spawn rate** and the bottom row sets the **loot rate**:
  `0x` · `-1.0` · `-0.1` · *current value* · `+0.1` · `+1.0` · `reset to 1x`
- Values range from 0x to 20x.

The **effective rate** of a mob is `global × per-mob`. For example, a global spawn rate of 2x and a zombie spawn rate of 1.5x gives zombies 3x.

## How the rates work

- **Spawn rate**, which covers natural spawning and the animals placed when new chunks generate:
  - Below 1x, each spawn is randomly cancelled (0.5x cancels about half). 0x means the mob never spawns naturally.
  - Above 1x, extra copies spawn next to each mob that spawns (2x means one extra, 2.5x means one or two extras).
  - Spawners, spawn eggs, `/summon`, raids and other non-natural spawns are not affected. Vanilla mob caps still decide when natural spawning stops, but the extra copies don't count towards that check.
- **Loot rate:** the mob's loot table is rolled that many times when it dies. 0x means no loot-table drops, 3x means triple drops, and 1.5x means one or two rolls. Equipment the mob was holding and XP are unchanged.

## Building

Requires Java 21.

```
./gradlew build
```

The jar is written to `build/libs/mob-spawn-rates-<version>.jar`. Put it in your `mods` folder together with [Fabric API](https://modrinth.com/mod/fabric-api).

# Mob Spawn & Loot Rates (Fabric & Paper 1.21.11)

Comes in two versions:

| | Fabric mod | Paper plugin |
|---|---|---|
| Jar | `dist/mob-spawn-rates-1.1.0.jar` | `dist/mob-spawn-rates-paper-1.1.0.jar` |
| Install in | `mods/` (also needs Fabric API) | `plugins/` |
| Source | `src/` | `paper/` |
| Settings file | `config/mobspawnrates.json` | `plugins/MobSpawnRates/rates.yml` |
| Who can use it | operators (permission level 2) | permission `mobspawnrates.admin` (default: operators) |

Both versions have the same commands and chest GUI. Players who also have the **Fabric mod on their client** get the slider screen on either server type, including Paper.

Change how many mobs spawn and how much loot they drop, in-game, through a GUI.

- **Slider screen:** if your client has the mod installed, `/loot gui` opens a screen with a spawn slider and a loot slider for every mob, a search box, and per-mob reset buttons. Values are precise to 0.01.
- **Works server-only:** players without the mod on their client get a chest-style GUI instead. It's a normal 9x6 chest menu, so it works with a vanilla client. The mod must be installed on the server, or on your own client for singleplayer.
- **Operators only:** the command and the GUI need permission level 2 (the same level as vanilla `/loot`). If someone is de-opped while the GUI is open, it closes.
- Settings are saved to `config/mobspawnrates.json` as soon as you change them.

## Commands

| Command | What it does |
|---|---|
| `/loot gui` | Opens the slider screen, or the chest GUI if your client doesn't have the mod |
| `/loot gui chest` | Always opens the chest GUI |
| `/loot reload` | Reloads `config/mobspawnrates.json` from disk |

Vanilla `/loot spawn|give|insert|replace` still works. The mod only adds the `gui` and `reload` subcommands.

## Using the slider screen

- Drag a slider to set a value. The left half of each slider covers **0x-2x** for fine control, and the right half covers **2x-20x**.
- For exact values, click a slider and use the **arrow keys**: **±0.01** per press, or **±0.1** with Shift.
- Type in the search box to filter mobs. The top row is the **global** multiplier.
- **↺** resets that row to 1x. **Reset all** needs two clicks.
- Hover a slider to see the effective rate after the global multiplier is applied.
- The server checks operator permission for every change, so a modified client can't bypass it.

## Using the chest GUI

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

## Paper plugin notes

- **`/loot` on Paper:** Paper lets a plugin take over a vanilla command name. Everything except `gui` and `reload` is passed on to the vanilla command, so `/loot spawn|give|insert|replace ...` still works. The vanilla command is also still available as `/minecraft:loot`.
- **New-chunk animals:** Paper doesn't fire spawn events for animals created with new chunks. The plugin handles them when the new chunk first loads. Mobs that belong to structures, such as villagers and iron golems, are not affected.
- **Loot at 0x:** the whole drop list is removed, including any equipment the mob was holding. Above 1x, only the loot table is rolled again, as in the Fabric version.

## Building

Requires Java 21.

```
./gradlew build            # Fabric mod  -> build/libs/
./gradlew -p paper build   # Paper plugin -> paper/build/libs/
```

Put the Fabric jar in your `mods` folder together with [Fabric API](https://modrinth.com/mod/fabric-api). Put the Paper jar in your server's `plugins` folder.

# Style Mod

**Style Mod** is a client-side Fabric mod for Minecraft PvP, inspired by *ULTRAKILL*. Earn style points with confirmed hits, keep your combo alive, climb the ranks from **D** to **ULTRAKILL**, and see your score when you defeat a player.

## How it works

- Confirmed attacks award style points. The HUD only reacts to hits confirmed by the server.
- Your combo meter decays over time. Its level sets your rank and the multiplier applied to style points.
- Movement speed also affects points gained and lost.
- Taking damage reduces style and breaks your sprint-hit combo.
- The jump-reset timer grades your jump timing after combat damage.
- A kill displays your total style score. Reset behavior on kills, deaths, and gamemode changes is configurable.
- The combo bar briefly pulses when you gain style.

## Style rewards

- Mace hit
- Hit
- Sprint hit
- Close call
- Shield Break
- Ranged
- Critical
- Kill

## Ranks

**D → C → B → A → S → SS → SSS → ULTRAKILL**

The **ULTRAKILL** rank unlocks before the combo meter is completely full.

## Configuration

Highly configurable. The config file is config/style-mod.json in the Minecraft game directory.

### Reset rules

All reset rules default to `true`:

- resetOnGameModeChange
- resetOnKill
- resetOnDeath

### Commands

Client-side commands, no permissions required:

- `/style info` — current score, combo, rank and difficulty
- `/style reset` — reset style and combat tracking
- `/style difficulty [harmless|lenient|standard|violent|brutal]` — show or change difficulty
- `/style config` — show current settings
- `/style config show <part> <true|false>` — toggle a HUD part (`rank`, `score`, `multiplier`, `combo_bar`, `bonuses`, `jump_reset`, `kill_banner`, `lunge_counter`)
- `/style config hudx <px>` / `hudy <px>` — move the HUD
- `/style config jump_reset|reset_on_gamemode|reset_on_kill|reset_on_death <true|false>`
- `/style config reload` — reload `style-mod.json` from disk

## Installation

Requires **Minecraft 1.21.11**, **Fabric Loader**, and [Fabric API](https://modrinth.com/mod/fabric-api). Place the mod JAR in the game's `mods` folder and launch the Fabric profile.

## Building from source

Requires JDK 21.

```
./gradlew build
```

The mod JAR is written to `build/libs/`.

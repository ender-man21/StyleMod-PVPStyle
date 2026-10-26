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

## Installation

Requires **Minecraft 1.21.11**, **Fabric Loader**, and [Fabric API](https://modrinth.com/mod/fabric-api). Place the mod JAR in the game's `mods` folder and launch the Fabric profile.
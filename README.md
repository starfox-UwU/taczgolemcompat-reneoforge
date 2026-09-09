# TaCZ Golem Compat (1.21.1 NeoForge)

Port of [TaCZGolemCompat](https://github.com/Minecraft-LightLand/TaCZGolemCompat) (1.20.1 Forge) to Minecraft 1.21.1 NeoForge.

Compat layer between TaCZ (Timeless and Classics Zero) and ModularGolems:
Allows Modular Golems to wield guns from Timeless and Classics Zero.

## Features

- Humanoid golems equipped with TaCZ guns will use them in ranged combat
  (aiming, shooting, reloading, bolting, melee bayonet attacks), driven by a
  custom `TaczSmartGoal` adapted to mob_weapon_api 3.x.
- Friendly-fire prevention mixins on TaCZ:
  - `EntityKineticBulletMixin` — bullets skip entities the golem cannot attack
  - `EntityUtilMixin` — entity path hit-scan skips golem-allied entities
  - `LivingEntityAmmoCheckMixin` — golems ignore ammo checks when hostile

## Dependencies (required)

| Mod | Version |
|---|---|
| NeoForge | 21.1.249 |
| Minecraft | 1.21.1 |
| L2Library | 3.0.0+ |
| Modular Golems | 3.1.0+ |
| TaCZ (Timeless and Classics Zero) | 1.1.8+ (NeoForge 1.21.1) |
| mob_weapon_api | 3.0.0+ (bundled with Modular Golems via JarJar) |

## Building

Local dependency jars used for compilation live in `./libs`:

- `tacz-neoforge-1.21.1-1.1.8-hotfix-r6.jar` (built from the TaCZ NeoForge sources)
- `modulargolems-3.1.40.jar` (built from ModularGolems-1.21 sources)
- `l2library-3.0.4-slim.jar`, `l2core-3.0.8+8.jar`, `l2serial-3.1.1.jar`
- `mob_weapon_api-3.0.20.jar`
- `mixinextras-neoforge-0.5.3.jar` (extracted from the NeoForge universal jar)

```shell
gradlew build
```

The output jar is `build/libs/taczgolemcompat-1.0.0.jar`.

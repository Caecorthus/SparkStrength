# SparkStrength Working Context

Use this file as a short handoff for the current code and verified contracts.

## Current Baseline

- Repository baseline: `b223d98` (`Release 1.0.3`).
- Published Engineer feature commit: `85969bf` (`engineer strength`).
- Current architecture includes role-owned Detective, Toxicologist, Attendant,
  Corrupt Cop, Noisemaker, Professor, Veteran, Engineer, shared economy, and
  tablet Modules.
- SparkFactionAPI is not a build, metadata, import, or runtime dependency.

## Engineer Responsibility Split

- `role/engineer/EngineerRules`: ids, prices, radius, and timing values.
- `item/CaptureDeviceItem`: placement, spawn, placement sound/replay, and item
  consumption.
- `entity/CaptureDeviceEntity`: tracked/NBT state, capture lifecycle and
  candidate lookup, stun/report effects, sounds, and trigger/expiry replay.
- `component/engineer/EngineerStunnedPlayerComponent`: synced lock state,
  persistence, server lock enforcement, and release replay.
- `role/engineer/EngineerCaptureDeviceService`: player and round cleanup only.
- `role/engineer/EngineerPowerRestorationService`: blackout reset, killer payout,
  and Wathe `blackout` cooldown update.
- `mixin/wathe/PlayerShopComponentAccessor`: exact private
  `PlayerShopComponent.cooldowns` seam; do not broaden it.

## Protected Engineer Contracts

- Item ids: `sparkstrength:capture_device`,
  `sparkstrength:power_restoration`.
- Entity id and dimensions: `sparkstrength:capture_device`, `0.35F x 0.08F`.
- Component id: `sparkstrength:engineer_stunned`.
- Replay ids: `capture_device_placed`, `capture_device_triggered`,
  `capture_device_released`, `capture_device_expired` under `sparkstrength`.
- Capture behavior: radius `5.0`, stun `5s`, lifetime `120s`; owner is excluded,
  native world query order is preserved, and expiry wins at the boundary tick.
- Entity NBT: `Owner`, `CeilingMounted`, `LifetimeTicks`.
- Stun component NBT: `StunTicks`, `LockedX`, `LockedY`, `LockedZ`.
- Report custom data: `SparkStrengthEngineerCaptureReport` with `Owner` UUID.

## Cross-Mod Seams

- Wathe and NoellesRoles are required.
- SparkTraits is optional. Reflect only public
  `dev.caecorthus.sparktraits.api.SparkTraitsApi.hasActiveTrait(PlayerEntity,
  Identifier)` with exact `sparktraits:impostor`; missing/incompatible APIs fail
  closed to `false`.
- SparkWitch, SparkAssist, and SparkFactionAPI have no direct dependency seam.

## Verification

Use Java 21 and run the architecture gate, then the sequential clean build:

```bash
./gradlew verifyArchitecture --no-daemon --no-watch-fs --console=plain
./gradlew clean build --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1
git diff --check
```

Do not overwrite unrelated local changes, including wrapper-mode changes, while
performing architecture maintenance.

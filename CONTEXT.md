# SparkStrength Working Context

Use this file as a short handoff. `ARCHITECTURE.md` remains the binding
constitution and `ARCHITECTURE_LOGS.md` remains the history/closure register.

## Current Baseline

- Repository baseline: `b223d98` (`Release 1.0.3`).
- Published Engineer feature commit: `85969bf` (`engineer strength`).
- The 2026-07-09 architecture and Engineer deepening described below is a
  working-tree slice until it receives its own commit; do not attribute it to
  `b223d98`.
- Current architecture includes role-owned Detective, Toxicologist, Attendant,
  Corrupt Cop, Noisemaker, Professor, Veteran, Engineer, shared economy, and
  tablet Modules.
- SparkFactionAPI is not a build, metadata, import, or runtime dependency.

## Engineer Ownership

- `role/engineer/EngineerRules`: ids, prices, radius/timing, candidate policy,
  and capture tick decisions.
- `role/engineer/EngineerCaptureDeviceService`: placement, spawn, candidate
  lookup, stun/report effects, sounds, replay, and round cleanup.
- `entity/CaptureDeviceEntity`: DataTracker/NBT/tick Adapter only.
- `item/CaptureDeviceItem`: block-use Adapter only.
- `component/engineer/EngineerStunnedPlayerComponent`: synced lock state,
  persistence, and server lock enforcement.
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

Use Java 21 and run focused rules first, then the architecture gate, then the
sequential clean build:

```bash
./gradlew test --tests annina.sparkstrength.role.engineer.EngineerRulesTest --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1
./gradlew verifyArchitecture --no-daemon --no-watch-fs --console=plain
./gradlew clean build --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1
git diff --check
```

Do not overwrite unrelated local changes, including wrapper-mode changes, while
performing architecture maintenance.

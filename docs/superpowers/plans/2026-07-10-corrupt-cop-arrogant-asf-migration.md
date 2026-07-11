# Corrupt Cop Arrogant ASF Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire `sparktraits:arrogant_asf` as a SparkTraits trait and preserve its complete active ability as a direct `noellesroles:corrupt_cop` enhancement owned by SparkStrength.

**Architecture:** SparkStrength owns role rules and server authority under `role/corruptcop/`, owner-only runtime state under `component/corruptcop/`, and client HUD/audio under `client/role/corruptcop/`. Thin mixins reuse NoellesRoles' existing `AbilityC2SPacket` receiver and Minecraft's post-normalization `Entity.updateVelocity` hook; SparkTraits removes registration and behavior while retaining its trailing sync field as a false tombstone. SparkAssist updates only the event-sound namespace, and SparkFactionAPI receives a documentation correction.

**Tech Stack:** Java 21, Fabric Loader/API, Yarn 1.21.1, Cardinal Components API, Sponge Mixin, JUnit 5, Gradle/Loom.

## Global Constraints

- Preserve exact role id `noellesroles:corrupt_cop`.
- Preserve lateral multiplier `1.75f`, HUD color `0x193264`, and resume window `200` ticks.
- Preserve forward-only movement, all unrelated roles/traits, NoellesRoles moment logic, tablet behavior, packet ids, component ids, and replay ids.
- Add no SparkTraits or SparkFactionAPI dependency to SparkStrength.
- Reuse NoellesRoles `AbilityC2SPacket`; add no new SparkStrength C2S payload.
- Keep the SparkTraits trailing sync field as a written-false/read-and-discard tombstone.
- Do not overwrite existing unrelated dirty changes and do not commit or push unless explicitly requested.

---

### Task 1: SparkStrength Corrupt Cop Rules And State

**Files:**
- Modify: `src/main/java/annina/sparkstrength/role/corruptcop/CorruptCopRules.java`
- Create: `src/main/java/annina/sparkstrength/component/corruptcop/CorruptCopAbilityComponent.java`
- Create: `src/main/java/annina/sparkstrength/role/corruptcop/CorruptCopAbilityService.java`
- Modify: `src/main/java/annina/sparkstrength/component/SparkStrengthComponents.java`
- Modify: `src/main/java/annina/sparkstrength/event/SparkStrengthEvents.java`
- Modify: `src/main/resources/fabric.mod.json`
- Test: `src/test/java/annina/sparkstrength/role/corruptcop/CorruptCopRulesTest.java`

**Interfaces:**
- Produces: `CorruptCopRules.nextAbilityActive(boolean, boolean)`, `CorruptCopRules.lateralVelocityBonus(Vec3d, float, float, boolean, boolean, boolean)`, `CorruptCopAbilityService.toggle(ServerPlayerEntity)`, `CorruptCopAbilityService.reset(ServerPlayerEntity)`.
- Produces: owner-only `CorruptCopAbilityComponent.KEY` whose `isActive()` state defaults to false and is never copied across respawn.

- [ ] Write JUnit cases proving non-Corrupt-Cop rejection, active toggle, dead/inactive zero vectors, pure A/D `1.75x` lateral share, diagonal lateral-only boost, and forward-only zero bonus.
- [ ] Run `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*CorruptCopRulesTest'` and verify the new test fails before production code exists.
- [ ] Add constants `ABILITY_COLOR = 0x193264`, `LATERAL_SPEED_MULTIPLIER = 1.75f`, and the pure rule methods to `CorruptCopRules`.
- [ ] Add an owner-only auto-synced runtime component. `writeToNbt` writes no active state and `readFromNbt` resets false; `writeSyncPacket`/`applySyncPacket` transfer one boolean.
- [ ] Add the server service that validates role, alive/survival, and not swallowed before toggling. Register reset handling without changing other role services.
- [ ] Register component id `sparkstrength:corrupt_cop_ability` in Java, `fabric.mod.json`, and the architecture stable-surface documentation.
- [ ] Re-run the focused JUnit test and expect PASS.

### Task 2: SparkStrength Ability Packet And Movement Adapters

**Files:**
- Create: `src/main/java/annina/sparkstrength/mixin/corruptcop/CorruptCopAbilityPacketMixin.java`
- Create: `src/main/java/annina/sparkstrength/mixin/corruptcop/CorruptCopLateralVelocityMixin.java`
- Modify: `src/main/resources/sparkstrength.mixins.json`

**Interfaces:**
- Consumes: `CorruptCopAbilityService.toggle(ServerPlayerEntity)` and `CorruptCopRules.lateralVelocityBonus(...)`.
- Produces: no new packet id; the existing NoellesRoles shared ability receiver is cancelled only after a handled Corrupt Cop toggle.

- [ ] Add a HEAD injection for NoellesRoles' current `AbilityC2SPacket` receiver lambda targets. The mixin reads only `context.player()`, delegates, and cancels only on `true`.
- [ ] Add an `Entity.updateVelocity` TAIL adapter. It ignores non-player entities and delegates all role/state/vector policy to the Corrupt Cop module.
- [ ] Register both mixins and run `compileJava` to prove the exact NoellesRoles lambda targets and Minecraft signature resolve.

### Task 3: SparkStrength HUD And TAKEDISKRUSH Audio

**Files:**
- Create: `src/main/java/annina/sparkstrength/role/corruptcop/CorruptCopMusicRules.java`
- Create: `src/main/java/annina/sparkstrength/SparkStrengthSounds.java`
- Create: `src/client/java/annina/sparkstrength/client/role/corruptcop/CorruptCopAbilityHud.java`
- Create: `src/client/java/annina/sparkstrength/client/role/corruptcop/CorruptCopMusicController.java`
- Create: `src/client/java/annina/sparkstrength/client/role/corruptcop/CorruptCopMusicInstance.java`
- Create: `src/client/java/annina/sparkstrength/client/role/corruptcop/CorruptCopSoundAccess.java`
- Create: `src/client/java/annina/sparkstrength/client/mixin/corruptcop/CorruptCopHudMixin.java`
- Create: `src/client/java/annina/sparkstrength/client/mixin/corruptcop/SoundManagerAccessor.java`
- Create: `src/client/java/annina/sparkstrength/client/mixin/corruptcop/SoundSystemAccessor.java`
- Modify: `src/main/java/annina/sparkstrength/SparkStrength.java`
- Modify: `src/client/java/annina/sparkstrength/client/SparkStrengthClient.java`
- Modify: `src/client/resources/sparkstrength.client.mixins.json`
- Create: `src/main/resources/assets/sparkstrength/sounds.json`
- Create: `src/main/resources/assets/sparkstrength/sounds/music/takediskrush.ogg`
- Modify: `src/main/resources/assets/sparkstrength/lang/en_us.json`
- Modify: `src/main/resources/assets/sparkstrength/lang/zh_cn.json`
- Test: `src/test/java/annina/sparkstrength/role/corruptcop/CorruptCopMusicRulesTest.java`

**Interfaces:**
- Produces: `sparkstrength:music.takediskrush`; `CorruptCopMusicRules.RESUME_WINDOW_TICKS = 200`.
- Consumes: the owner-synced component plus current Corrupt Cop role state.

- [ ] Write timing tests for retain at tick `199`, discard at `200`, and ceiling-seconds countdown.
- [ ] Run the focused test and verify it fails before the music rules exist.
- [ ] Port the exact owner-only ambient loop, pause/resume source access, HUD placement, ability-bind text, and countdown behavior into Corrupt Cop-owned client classes.
- [ ] Register the sound event and client tick. Move the OGG bytes and sound JSON entry to the `sparkstrength` namespace with stream volume `0.9`.
- [ ] Add only the active/inactive/countdown/subtitle translations; do not add a trait name or description.
- [ ] Run focused tests and `compileClientJava`; expect PASS.

### Task 4: Retire SparkTraits Trait And Migrate Legacy State

**Files:**
- Delete: all `ArrogantAsf*` domain, movement, HUD, and audio files
- Delete: `src/main/resources/assets/sparktraits/sounds/music/takediskrush.ogg`
- Modify: `SparkTraitsBuiltInTraits`, `TraitAssignmentService`, `NoellesRolesPacketMixin`, `TraitPlayerComponent`, `SparkTraitsClient`, mixin JSONs, `SparkTraitsSounds`, `sounds.json`, and both language files
- Modify: SparkTraits architecture/context documentation
- Test: focused retired-id and packet tombstone tests under `src/test/java`

**Interfaces:**
- Retires: `sparktraits:arrogant_asf`, without changing other trait ids or sound entries.
- Preserves: trailing component sync field 17 as `writeBoolean(false)` and optional read/discard.

- [ ] Add focused tests/static contract checks for no built-in registration/assignment and for exact legacy-id filtering.
- [ ] Remove the trait registration and forced assignment first.
- [ ] Add a narrow migration constant/filter that removes only `sparktraits:arrogant_asf` from active, pending, revealed, disabled, and round/death snapshot state loaded from old data.
- [ ] Remove runtime state/methods while retaining the false sync tombstone.
- [ ] Remove all Arrogant gameplay/client code and resources, preserving Depression entries byte-for-byte outside necessary JSON commas.
- [ ] Update architecture documentation to mark the id retired and field 17 reserved.
- [ ] Run Java 21 focused tests, `verifyArchitecture`, and `clean build`.

### Task 5: Downstream Sound Catalog And Documentation

**Files:**
- Modify: SparkAssist event-sound catalog/rules tests and only necessary translations
- Modify: `../SparkFactionAPI/SPARK_MOD_FEATURES.md`

**Interfaces:**
- Rebinds event sound detection to `sparkstrength:music.takediskrush`.
- Preserves the serialized event-volume setting key/group id `arrogant_asf_music`.

- [ ] Change SparkAssist's sound-event and sound-file namespace matches from SparkTraits to SparkStrength.
- [ ] Update focused tests while preserving the user setting id and label unless the label explicitly calls it a trait.
- [ ] Correct the feature document to describe a SparkStrength direct Corrupt Cop ability.
- [ ] Run SparkAssist Java 21 tests/build.

### Task 6: Coordinated Verification

**Files:**
- Modify: verification inputs only where the new/retired contracts require it. Root architecture documents remain untracked.

- [ ] Run `git diff --check` separately in SparkStrength, SparkTraits, SparkAssist, and SparkFactionAPI.
- [ ] Run Java 21 `clean test verifyArchitecture build` in SparkStrength.
- [ ] Run Java 21 `clean test verifyArchitecture build` in SparkTraits and SparkAssist when those tasks exist; otherwise run `clean test build` and report the absent task honestly.
- [ ] Inspect jars to prove TAKEDISKRUSH exists only under SparkStrength and no `ArrogantAsf` class/resource remains in SparkTraits.
- [ ] Search all local Spark-family repositories for `sparktraits:arrogant_asf` and the old sound namespace; allow only explicit retirement/migration documentation and tombstone tests.
- [ ] Review diffs against pre-existing dirty changes and report exactly what was changed, what was verified, and what still needs live Minecraft acceptance.

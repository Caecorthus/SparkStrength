# SparkStrength Architecture Constitution

This document is mandatory for all future agents working in this repository.
It records the current local architecture and the rules for changing it. It is
not a changelog, not a backlog, and not blanket permission to refactor old code.

Historical notes, observed completed boards, stale plans, and retired names live
in [ARCHITECTURE_LOGS.md](ARCHITECTURE_LOGS.md).

## Mandatory Rules

1. Read this file before changing code.

2. Read `ARCHITECTURE_LOGS.md` before moving, deleting, renaming, splitting, or
   merging existing Modules.

   The logs explain why old seams should not be restored. They do not override
   this constitution and do not authorize edits.

3. Protect unrelated roles, traits, commands, packets, resources, UI behavior,
   and external mod behavior.

   If a task touches one role or one item, prove it does not broaden into
   another role, the tablet system, SparkTraits compatibility, Wathe base rules,
   or NoellesRoles base rules unless the owner explicitly approves that wider
   scope.

4. There is no public downstream `api/` package in the current SparkStrength
   architecture.

   Treat every Java package as internal unless this file names it as stable.
   Do not add a new public Interface, hard dependency, exposed service class, or
   downstream registration surface without owner-approved scope, downstream
   impact, version plan, and verification plan.

5. Current direct gameplay dependencies are Wathe and NoellesRoles.

   SparkTraits is optional and reflection/registry-gated. SparkFactionAPI is not
   a current local dependency. Do not add or restore a SparkFactionAPI dependency
   unless a task explicitly approves the new seam and explains why Wathe,
   NoellesRoles, or optional SparkTraits compatibility is not enough.

6. Role-owned behavior stays in the owning role package.

   Do not recreate a catch-all NoellesRoles enhancement module. Shared glue is
   allowed only when the shared concept is real:

   - `role/economy/` may aggregate money visibility and task rewards.
   - `component/` may register and store CCA state.
   - `event/` may register hooks and delegate.
   - `network/` may register packets and dispatch to services.

   Gameplay rules, prices, cooldowns, target rules, shop entries, and highlight
   policy must stay in the owning role or subsystem Module.

7. `mixin/` classes are thin Adapters at Wathe, NoellesRoles, Minecraft, or
   client-rendering seams.

   A mixin may:

   - Locate the injection point.
   - Read the minimum required context.
   - Convert external types into the owning Module's Interface.
   - Delegate to a domain service or rule Module.

   A mixin must not own shop rules, role predicates, cooldown rules, packet
   authority, tablet state, replay semantics, or cross-mod compatibility policy.

8. Server services are authoritative for privileged actions.

   C2S packets and client screens are requests and display only. Server-side
   services must re-check role, alive/in-game state, inventory, cooldowns,
   tablet hotbar possession, meeting eligibility, vote locks, and target
   validity before mutating state.

9. Components store state and sync visibility; they are not gameplay rule
   buckets.

   CCA components may preserve runtime state, NBT keys, sync filtering, and tick
   timers needed for their domain. They must not absorb unrelated role logic just
   because the state is convenient to access there.

10. Code shape should stay small and single-purpose.

    - In all cases, code aesthetics, human readability, and correct functionality
      are the highest priorities. Do not game the guidance by making code cramped,
      obscure, or harder to maintain.
    - A class may own only one responsibility. If it has more than one reason to
      change, split or delegate through the owning Module instead of adding more
      behavior.
    - More than 5 parameters, methods beyond roughly 30-70 lines, methods above
      100 lines, and classes beyond roughly 200-300 lines are review triggers,
      not mechanical hard caps.
    - Blank lines and comments do not count toward method, function, or class line
      guidance.
    - External override, mixin, codec, and event signatures may exceed parameter
      guidance when the provider owns the signature. Do not hide parameters in an
      unhelpful wrapper merely to satisfy a number.
    - If a trigger exposes mixed ownership or poor locality, use the structural
      approval process. Existing over-trigger code is not automatic permission to
      refactor.

11. Comments must be English and Chinese when they explain:

    - Public or stable Interface semantics.
    - Wathe, NoellesRoles, Minecraft, Fabric, LambDynamicLights, or Spark-family
      seam behavior.
    - Mixin injection reasons.
    - Client/server authority or sync visibility rules.
    - Cross-mod compatibility rules.
    - Legacy retention or migration reasons.

    Do not add noise comments to self-explanatory code.

12. Test depth follows risk and bootstrap cost.

    - Add focused automated tests for pure rules, protocol/schema contracts,
      visibility decisions, compatibility targets, and regressions that can run
      without a Minecraft server bootstrap.
    - Do not add fake tests that merely assert source strings or mock away the
      behavior under test. Registry-, CCA-, rendering-, and mixin-heavy behavior
      may use compile, static-contract, jar, and integration gates when a reliable
      unit seam does not exist.
    - Production reset helpers must not exist only for tests.
    - Tests supplement, rather than replace, Java 21 builds and the smallest
      meaningful client/integration verification for the touched surface.

13. Metadata, packet schemas, persisted schemas, and resources must match code.

    Keep `fabric.mod.json`, mixin configs, component ids, packet ids,
    translation keys, item models, textures, replay formatter ids, and
    entrypoints aligned with the registered code. Packet direction, codec field
    order/type, validation authority, and fallback behavior are stable contracts.
    NBT key names, element types, UUID ownership, default behavior for absent
    keys, and CCA copy/sync semantics are stable persisted contracts. Do not
    leave stale ids or silently reinterpret stored fields after changing the
    owning Module; require an approved migration when compatibility would break.

14. Before structural changes, request owner approval with:

    - Board: the named architecture area being changed.
    - Reason: the friction that makes the old Module shape unsafe or costly to
      keep.
    - Old code scope: exact packages, files, and existing methods/helpers that
      will be moved, deleted, renamed, or rewritten.
    - New Module shape: the proposed package/module name and its intended
      Interface, including allowed responsibilities.
    - Forbidden scope: files, methods, policies, ordering, fallback behavior,
      resources, packet ids, component ids, and downstream contracts that must
      not change.
    - Behavior invariants: null behavior, fallback behavior, event ordering,
      priority, role isolation, trait isolation, CCA sync/NBT semantics, packet
      semantics, and user-visible text that must be preserved.
    - Downstream impact: whether SparkTraits, SparkWitch, SparkFactionAPI,
      SparkAssist, Wathe, NoellesRoles, tests, releases, or user workflows must
      change.
    - Verification plan: exact local tests, build commands, static checks, jar
      checks, and downstream searches/checks.
    - Downstream notes needed: yes/no.

    If any item is unknown, perform a read-only review before requesting
    approval. Do not fill gaps by guessing during implementation.

## Stable Surface

These surfaces are stable unless a task explicitly approves a breaking change
and includes impact plus verification.

- Mod id: `sparkstrength`.
- Main entrypoint: `annina.sparkstrength.SparkStrength`.
- Client entrypoint: `annina.sparkstrength.client.SparkStrengthClient`.
- Cardinal Components entrypoint:
  `annina.sparkstrength.component.SparkStrengthComponents`.
- Dynamic lights entrypoints:
  `annina.sparkstrength.client.role.attendant.FlashlightDynamicLightsInitializer`.
- Item ids:
  - `sparkstrength:capsule`
  - `sparkstrength:flashlight`
  - `sparkstrength:tablet`
  - `sparkstrength:invisibility_serum`
  - `sparkstrength:doorpassing_potion`
  - `sparkstrength:sedative`
  - `sparkstrength:truth_serum`
  - `sparkstrength:capture_device`
  - `sparkstrength:power_restoration`
- Entity id:
  - `sparkstrength:capsule`
  - `sparkstrength:capture_device`
- Component ids:
  - `sparkstrength:corrupt_cop_ability`
  - `sparkstrength:noisemaker_glow_user`
  - `sparkstrength:noisemaker_glow_target`
  - `sparkstrength:professor_serum_user`
  - `sparkstrength:professor_serum_target`
  - `sparkstrength:criminologist_player`
  - `sparkstrength:veteran_knife`
  - `sparkstrength:engineer_stunned`
  - `sparkstrength:criminologist_world`
  - `sparkstrength:tablet_world`
- Packet ids:
  - `sparkstrength:noisemaker_glow`
  - `sparkstrength:professor_remote_feed`
  - `sparkstrength:select_criminologist_target`
  - `sparkstrength:open_criminologist_screen`
  - `sparkstrength:request_tablet_snapshot`
  - `sparkstrength:send_tablet_chat`
  - `sparkstrength:call_tablet_meeting`
  - `sparkstrength:cast_tablet_vote`
  - `sparkstrength:confirm_tablet_vote`
  - `sparkstrength:approve_suspect_removal`
  - `sparkstrength:open_tablet_screen`
  - `sparkstrength:sync_tablet_snapshot`
  - `sparkstrength:sync_veteran_blackout`
- Command literals:
  - `sparkstrength:emergencyMeetingChances`
  - `sparkstrength:voteTime`
  - `sparkstength:voteTime`
- Replay event ids:
  - `sparkstrength:noisemaker_glow_started`
  - `sparkstrength:noisemaker_glow_ended`
  - `sparkstrength:professor_serum_fed`
  - `sparkstrength:professor_invisibility_ended`
  - `sparkstrength:professor_doorpassing_ended`
  - `sparkstrength:professor_sedative_ended`
  - `sparkstrength:professor_truth_revealed`
  - `sparkstrength:capture_device_placed`
  - `sparkstrength:capture_device_triggered`
  - `sparkstrength:capture_device_released`
  - `sparkstrength:capture_device_expired`
- Translation key families under `item.sparkstrength.*`,
  `shop.sparkstrength.*`, `hud.sparkstrength.*`, `screen.sparkstrength.*`,
  `message.sparkstrength.*`, `ui.sparkstrength.*`,
  `commands.sparkstrength.*`, and `replay.global.sparkstrength.*`.

Current shop price constants are behavior surfaces owned by their role Modules.
Do not duplicate these values in UI, packet, mixin, or component code:

- Professor serum prices in `ProfessorSerumRules`:
  - Sedative: `50`
  - Doorpassing potion: `75`
  - Invisibility serum: `125`
  - Truth serum: `125`
  - Refresh cooldown: `50`
- Engineer prices in `EngineerRules`:
  - Capture device: `125`
  - Power restoration: `300`
  - Post-restoration Wathe `blackout` cooldown: `90s`

External role/item ids used by rules are also part of the behavior contract:

- `wathe:veteran`
- `wathe:vigilante`
- `noellesroles:attendant`
- `noellesroles:corrupt_cop`
- `noellesroles:detective`
- `noellesroles:engineer`
- `noellesroles:neutral_master_key`
- `noellesroles:professor`
- `noellesroles:toxicologist`
- Optional SparkTraits ids such as `sparktraits:conscience_poisoner` and
  `sparktraits:impostor`. The reflection bridge may target only public
  `dev.caecorthus.sparktraits.api.SparkTraitsApi.hasActiveTrait(PlayerEntity,
  Identifier)` and must fail closed to `false` when unavailable.

### Corrupt Cop Ability Contracts

- The ability belongs only to `noellesroles:corrupt_cop`. It uses color
  `0x193264`, applies a `1.75F` multiplier only to the normalized lateral input
  share, and leaves forward input unchanged.
- `sparkstrength:corrupt_cop_ability` stores one runtime-only boolean. It is
  `RespawnCopyStrategy.NEVER_COPY`, syncs only to the owning player, writes one
  boolean in its sync packet, writes no NBT state, and loads as inactive.
- Ability requests reuse NoellesRoles `AbilityC2SPacket`; SparkStrength adds no
  payload id. The adapter cancels NoellesRoles handling only after a valid
  Corrupt Cop toggle was handled by the server service.
- Owner-only `sparkstrength:music.takediskrush` uses `AMBIENT`, retains the same
  paused source for fewer than `200` inactive ticks, and discards it at tick
  `200`.

### Engineer Persisted And External Contracts

- `CaptureDeviceEntity` keeps NBT keys `Owner` (UUID), `CeilingMounted`
  (boolean), and `LifetimeTicks` (int), plus tracked owner/ceiling state. Absent
  values retain empty-owner, floor-mounted `false`, and zero-tick defaults.
- `EngineerStunnedPlayerComponent` keeps `StunTicks` (int) and `LockedX`,
  `LockedY`, `LockedZ` (double), with numeric zero defaults when absent. It
  remains `RespawnCopyStrategy.NEVER_COPY` and auto-syncs stun state.
- Capture reports remain paper stacks marked under custom-data compound
  `SparkStrengthEngineerCaptureReport`, with owner UUID key `Owner`.
- Engineer adds no custom packet id or payload. Stun visibility uses the existing
  CCA component sync contract; future Engineer packets still require an
  explicit direction, codec field-order/type, authority, and fallback entry in
  this document before registration.
- Capture radius is `5.0`, stun is `5s`, lifetime is `120s`, and expiry wins
  over triggering on the lifetime-boundary tick.
- Capture entity dimensions remain `0.35F x 0.08F`, tracking range `8`, and
  tracking interval `10`.
- Candidate order is the native `ServerWorld#getEntitiesByClass` result order;
  do not sort or deduplicate it because report lore and per-player side effects
  follow that order.
- Capture replay payloads retain one `actor` UUID: the owner for placed/expired
  and the captured player for triggered/released. Offline expiry writes the
  owner's UUID through the extra NBT payload. No capture replay adds a `target`.
- Placement keeps `ENTITY_EXPERIENCE_ORB_PICKUP`; each captured player and an
  online report owner keep `BLOCK_ANVIL_LAND`, all in `PLAYERS` at volume/pitch
  `1.0F`. Release and expiry add no sound.
- The Wathe cooldown integration is the exact private-field seam
  `PlayerShopComponent.cooldowns`, exposed only by
  `mixin/wathe/PlayerShopComponentAccessor#sparkstrength$getCooldowns` with
  `@Accessor(value = "cooldowns", remap = false)`. Missing or renamed Wathe
  fields are a hard compatibility failure that requires source/version review,
  not a reflection fallback.

## Cross-Mod Contract Matrix

| Provider | Relationship | Allowed SparkStrength seam | Failure policy |
| --- | --- | --- | --- |
| Minecraft / Fabric / CCA | Required | Entity/item Adapters, lifecycle hooks, packets, components | Metadata and build must fail when incompatible. |
| Wathe | Required | Roles/game state, shops, replay, blackout, exact `PlayerShopComponent.cooldowns` accessor | Treat signature/field drift as a hard version mismatch. |
| NoellesRoles | Required | Role ids, Engineer role, hidden-equipment bridge | Treat role or binary drift as a hard version mismatch. |
| SparkTraits | Optional | Public `SparkTraitsApi` reflection plus registry-gated components | Fail closed; base SparkStrength behavior continues. |
| SparkFactionAPI | None | No build, metadata, import, or runtime dependency | Do not add without a separately approved public seam. |
| SparkWitch / SparkAssist | None | No direct dependency | Coordinate only when a shared public provider contract changes. |
| LambDynamicLights | Optional client compile seam | `src/lambdynlightsStub/java` and client initializer | Keep stubs out of the runtime jar. |

## Target Architecture

The current architecture is already package-based by role and subsystem. Keep
that shape stable; do not split it further without an approved board.

```text
src/main/java/annina/sparkstrength/
  SparkStrength.java
  SparkStrengthEntities.java
  SparkStrengthItems.java

  command/
    SparkStrengthCommands.java

  compat/
    SparkTraitsCompat.java

  component/
    SparkStrengthComponents.java
    corruptcop/
    detective/
    engineer/
    noisemaker/
    professor/
    tablet/
    veteran/

  entity/
    CapsuleEntity.java
    CaptureDeviceEntity.java

  event/
    SparkStrengthEvents.java

  item/
    CapsuleItem.java
    CaptureDeviceItem.java
    FlashlightItem.java
    PowerRestorationItem.java
    ProfessorSerumItem.java
    TabletItem.java

  mixin/
    corruptcop/
    noellesroles/
    professor/
    veteran/
    wathe/

  network/
    SparkStrengthPackets.java
    criminologist/
    noisemaker/
    professor/
    tablet/
    veteran/

  replay/
    SparkStrengthReplayFormatters.java

  role/
    attendant/
    corruptcop/
    detective/
    economy/
    engineer/
    noisemaker/
    professor/
    toxicologist/
    veteran/

  tablet/
    TabletAccess.java
    TabletRules.java
    TabletShopRules.java
    TabletShopService.java
    TabletStateService.java

src/client/java/annina/sparkstrength/client/
  SparkStrengthClient.java
  item/
  mixin/
    corruptcop/
    engineer/
  renderer/
    CaptureDeviceEntityRenderer.java
  role/
    corruptcop/
    engineer/
  screen/
  tablet/
  ui/

src/lambdynlightsStub/java/
  compile-only LambDynamicLights API shape

src/main/resources/
  fabric.mod.json
  sparkstrength.mixins.json
  assets/sparkstrength/

src/client/resources/
  sparkstrength.client.mixins.json
```

### Root Package

The root package owns bootstrap and registries:

- `SparkStrength` registers top-level systems.
- `SparkStrengthItems` owns stable item ids and item registration.
- `SparkStrengthEntities` owns stable entity ids and entity registration.

Root files must not grow role-specific gameplay, tablet runtime behavior,
client behavior, packet authority, or shop rules.

### `role/`

`role/` owns role-specific rules and services.

- `role/detective/`: Criminologist rules, corpse-killer evidence flow, target
  selection, tracking, reveal cadence, cooldown, and cost.
- `role/toxicologist/`: capsule shop eligibility, capsule price, poison display
  color rules, and optional SparkTraits blue-poison component probing.
- `role/attendant/`: starter flashlight rules, flashlight blackout interaction,
  and cone-light math.
- `role/corruptcop/`: Corrupt Cop role predicates, owner-only active ability,
  lateral movement and music timing rules, instinct highlight rules, neutral
  master key door result rules, and Wathe / NoellesRoles bridges.
- `role/noisemaker/`: backpack glow cooldown, target validation, glow start/end
  replay behavior, and death-response glow.
- `role/professor/`: serum type catalog, prices, durations, remote feed service,
  direct-use service, shop entries, highlight colors, and truth reveal behavior.
- `role/veteran/`: Veteran predicates, knife economy, knife shop, knife-use
  takeover service, blackout highlight rules, and blackout sync service.
- `role/economy/`: narrow shared money visibility, initial balance, and task
  reward glue. It may reference owning role rule Modules, but must not become a
  generic role-enhancement bucket.
- `role/engineer/`: Engineer identity and prices, capture lifecycle/candidate
  decisions, placement, stun/report/sound/replay orchestration, shop entries,
  power restoration, killer payout, and blackout cooldown integration.

### `tablet/`

`tablet/` owns the tablet network domain.

- `TabletRules` owns constants and pure calculations.
- `TabletShopRules` owns purchase/highlight constants and role purchase
  predicates.
- `TabletAccess` owns tablet hotbar and meeting participant checks.
- `TabletShopService` owns the Wathe shop entry for `sparkstrength:tablet`.
- `TabletStateService` owns authoritative server actions, snapshot creation,
  chat, meetings, suspect state transitions, cooldowns, and per-round cleanup.

Tablet purchase/economy eligibility and tablet runtime possession are different
concepts. Runtime access is based on holding `sparkstrength:tablet` in hotbar
slots `0-8`.

### `component/`

`component/` owns Cardinal Components registration, storage, sync filtering, NBT
keys, and narrow tick timers.

Player components use `RespawnCopyStrategy.NEVER_COPY` in current registration.
Do not change copy behavior, sync visibility, component ids, or NBT key meaning
without an approved migration plan.

`TabletWorldComponent` and `CriminologistWorldComponent` are server-owned world
state. They should not directly auto-sync private state to all clients; clients
receive filtered views through domain packets or screens.

`EngineerStunnedPlayerComponent` stores and syncs the lock point plus remaining
stun ticks. It may enforce the stored lock during server ticks, but capture
candidate, report, sound, replay, and placement policy stay in
`role/engineer/EngineerCaptureDeviceService` and `EngineerRules`.

### `network/`

`network/` owns packet definitions, codecs, registration, and dispatch.

`SparkStrengthPackets` registers channels and forwards actions to domain
services. It must not duplicate gameplay rules. Packet ids and payload fields
are stable user-facing compatibility surfaces.

### `item/` And `entity/`

Items and entities are Adapters around domain services where possible:

- `TabletItem` opens the tablet flow through `TabletStateService`.
- `ProfessorSerumItem` delegates use behavior to `ProfessorSerumService`.
- `FlashlightItem` owns item toggle state and notifies
  `FlashlightBlackoutService`.
- `CapsuleItem` and `CapsuleEntity` own capsule storage/projectile behavior and
  must preserve Wathe poison/drink/food semantics.
- `CaptureDeviceItem` adapts block use to
  `EngineerCaptureDeviceService#place`.
- `CaptureDeviceEntity` owns only Minecraft tracked state, NBT, rendering
  presence, and tick delegation. `EngineerCaptureDeviceService` owns candidate
  selection, trigger/expiry decisions, stun, capture-report creation, sounds,
  replay, and cleanup.
- `PowerRestorationItem` is a display/registry item; purchase authority stays in
  `EngineerPowerRestorationService`.

### `mixin/`

Common mixins adapt Wathe, NoellesRoles, or Minecraft seams to the owning
Module. Keep injection knowledge in mixins and behavior in rules/services.

`PlayerShopComponentAccessor` is the one approved Wathe private-state Adapter.
It exposes only the `cooldowns` map needed to set the post-restoration
`blackout` cooldown to `90s`; it must not grow accessors for unrelated shop
state.

### `client/`

Client code owns UI, HUD rendering, client packet receivers, client-only
highlight hooks, dynamic-light registration, and client mixins. It must not own
server authority.

`client/screen/tablet/` and `client/tablet/TabletClientHighlights` display
server snapshots and client-only outline decisions. They must not grant tablet
access or mutate meeting/suspect state locally.

### `compat/`

Compatibility code must be explicit and narrow.

Current SparkTraits behavior is optional:

- `SparkTraitsCompat` checks whether SparkTraits is loaded before reflecting the
  public `SparkTraitsApi.hasActiveTrait` facade with exact id
  `sparktraits:impostor`.
- Blue-poison display checks whether the SparkTraits data component id exists
  before querying an item stack.

Do not turn optional compatibility into a hard dependency without approval.

## Architecture Closure

There is no active architecture-refactor backlog. The current target shape is
good enough to protect role locality without turning SparkStrength into an
endless package-splitting exercise.

The published Engineer slice is part of the protected target shape. Its
capture entity/item are Minecraft Adapters, its stun component is state/tick
infrastructure, and its gameplay decisions/effects are owned by
`role/engineer/`. This is a truthful closure statement, not permission to move
other roles into Engineer or to keep splitting the service without a concrete
trigger.

### Stop Line

Do not split, move, rename, or delete Modules just because a finer shape is
possible. Future architecture work requires an owner-approved board and at
least one concrete trigger:

- A bug, crash, or compatibility issue proves the current Module shape caused
  drift.
- A new feature needs behavior at an existing seam and would otherwise add logic
  to the wrong Module.
- Tests, build output, source inspection, or this document show the
  implementation has diverged from the rules above.

Absent one of those triggers, keep the architecture stable and make local,
task-scoped changes only.

### Legacy And Migration Logs

Closed-board notes, retired names, stale implementation plans, and watch-only
constraints live in [ARCHITECTURE_LOGS.md](ARCHITECTURE_LOGS.md).

Read that file before touching retired, migrated, watch-only, or former legacy
modules. It records constraints for old seams only; all approval and
verification gates remain in this file.

## Approval Template For Structural Changes

Use this before modifying old architecture:

```text
Board:
Reason:
Old code scope:
New Module shape:
Forbidden scope:
Behavior invariants:
Downstream impact:
Verification plan:
Downstream notes needed: yes/no
```

No approval, no edit.

## Verification Expectations

For documentation-only changes:

```bash
git diff --check
```

For code changes:

```bash
./gradlew clean test verifyArchitecture build --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1
git diff --check
```

For client, mixin, resource, or jar-release changes, also run the smallest
meaningful client compile/build or jar-inspection checks for the touched surface.
Use Java 21 when the local launcher default drifts.

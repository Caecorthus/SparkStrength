# SparkStrength Architecture Logs

This file records architecture observations, closure notes, retired names, and
stale planning material extracted from the current local repository.

Read `ARCHITECTURE.md` first. These notes constrain old seams, but they do not
authorize edits. Approval, behavior-invariant, downstream-impact, and
verification rules remain in `ARCHITECTURE.md`.

## Snapshot

Observed from the local checkout at commit `b223d98` (`Release 1.0.3`) and
refreshed against the 2026-07-09 Engineer architecture working slice.

The source tree is already re-architected by role and subsystem. This log
should preserve that shape; it should not become a request to keep splitting
Modules.

## Architecture Closure Log

### Closed Boards

- Role-enhancement decomposition is complete in the current source. Role
  behavior lives under `role/detective/`, `role/toxicologist/`,
  `role/attendant/`, `role/corruptcop/`, `role/noisemaker/`,
  `role/professor/`, `role/veteran/`, and `role/engineer/`, with shared economy
  glue isolated in `role/economy/`. Do not restore a single NoellesRoles
  enhancement bucket.
- Tablet network ownership is complete enough to protect locality:
  `tablet/` owns server rules and actions, `component/tablet/` owns world state,
  `network/tablet/` owns packets and snapshots, and
  `client/screen/tablet/` plus `client/tablet/` own UI and client highlights.
- Client/server separation is complete in the current source. Server gameplay
  authority is in `src/main/java`; client screens, HUD, dynamic lights, client
  mixins, and client packet receivers are in `src/client/java`.
- SparkTraits compatibility is optional and narrow. `compat/SparkTraitsCompat`
  reflects public `SparkTraitsApi.hasActiveTrait` with exact
  `sparktraits:impostor` only when SparkTraits is loaded, and toxicologist
  blue-poison display checks the component registry before reading
  `sparktraits:conscience_poisoner`.
- SparkFactionAPI is not part of the current local dependency surface.
  `build.gradle`, `settings.gradle`, `fabric.mod.json`, `libs/`, and source
  imports show Wathe and NoellesRoles as direct gameplay dependencies, with no
  `sparkfactionapi` import or metadata dependency.
- Professor serum ownership is complete in the current source:
  `role/professor/` owns rules, types, shop, and service logic;
  `component/professor/` owns user/target state; `network/professor/` owns the
  remote-feed packet; client mixins and hooks own UI/crosshair/highlight
  behavior.
- The latest synced Professor price change stayed inside
  `role/professor/ProfessorSerumRules`: sedative `50`, doorpassing `75`,
  invisibility `125`, truth `125`, and refresh cooldown `50`. This confirms the
  architecture rule that price ownership remains in the Professor Module rather
  than UI, packet, mixin, or component code.
- Veteran ownership is complete in the current source: `role/veteran/` owns
  rules, shop, knife takeover, economy reward, and blackout sync;
  `component/veteran/` owns SparkStrength's multi-knife count; common and client
  mixins stay as Wathe knife adapters.
- LambDynamicLights compatibility is compile-isolated through
  `src/lambdynlightsStub/java`. The stubs are for client compilation shape only
  and must not be treated as shipped gameplay code.
- The Engineer publication at `85969bf` added `role/engineer/`,
  `component/engineer/`, `entity/CaptureDeviceEntity`, capture/power items,
  client Engineer mixins/hooks/renderer, `sparkstrength:engineer_stunned`, and
  four capture replay events. Commit `b223d98` released that surface as `1.0.3`.
- The approved 2026-07-09 deepening keeps the entity/item as Minecraft Adapters
  and moves placement, lifecycle/candidate policy, stun/report effects, sounds,
  replay, and cleanup behind `EngineerCaptureDeviceService` and
  `EngineerRules`. Its deletion test is explicit: removing the service would
  remove real domain ownership rather than only a forwarding layer.
- Engineer power restoration still uses the exact Wathe private seam
  `PlayerShopComponent.cooldowns` through `PlayerShopComponentAccessor`; no
  SparkFactionAPI dependency was introduced.

## Legacy Register

This register records names and seams that should not be casually restored. It
does not authorize edits.

### `NoellesRoleEnhancementService`,
`NoellesRoleEnhancementRules`, `NoellesRoleIds`, and
`RoleEnhancementPlayerComponent`

Status: Retired names.

Reason: the current source no longer uses one generic NoellesRoles enhancement
bucket. It separates role-specific rules, services, and components by domain.

Rule: do not recreate these names for convenience. Put behavior in the owning
role package:

- Detective/criminologist behavior belongs in `role/detective/` plus
  `component/detective/`.
- Toxicologist capsule behavior belongs in `role/toxicologist/`,
  `item/CapsuleItem`, and `entity/CapsuleEntity`.
- Attendant flashlight behavior belongs in `role/attendant/`,
  `item/FlashlightItem`, and the client dynamic-light Modules.
- Corrupt Cop behavior belongs in `role/corruptcop/` plus
  `component/corruptcop/`; Wathe door/highlight hooks, the NoellesRoles shared
  ability-packet adapter, the Minecraft velocity adapter, and client HUD/audio
  remain narrow delegates to that role-owned Module.
- Noisemaker glow behavior belongs in `role/noisemaker/` plus
  `component/noisemaker/`.
- Professor behavior belongs in `role/professor/` plus `component/professor/`.
- Veteran behavior belongs in `role/veteran/` plus `component/veteran/`.

### SparkFactionAPI Dependency

Status: Not current in this checkout.

Reason: older planning and memory may mention SparkFactionAPI, but the current
local SparkStrength source has no SparkFactionAPI dependency. Corrupt Cop uses
Wathe `DoorInteraction.EVENT`, NoellesRoles' existing ability packet, and a
Minecraft velocity adapter; tablet economy and highlights use local rules;
SparkTraits is optional through reflection/registry checks.

Rule: do not add SparkFactionAPI as a build/runtime dependency unless an
owner-approved board names the exact new public seam, forbidden scope,
downstream impact, and verification plan.

### SparkTraits Internal Effective-Trait Reflection

Status: Retired seam.

Reason: reflecting
`dev.caecorthus.sparktraits.impl.effective.EffectiveTraitService.hasImpostor`
coupled SparkStrength to an internal package and had already caused stale-jar
compatibility failures.

Rule: optional compatibility may reflect only the public
`dev.caecorthus.sparktraits.api.SparkTraitsApi` facade. Do not restore
`sparktraits.impl` reflection.

### `docs/superpowers/plans/2026-07-03-tablet-network.md`

Status: Historical plan; partially stale.

Reason: it preserves the original tablet implementation intent, but it still
mentions retired names such as `NoellesRoleEnhancement*`, a SparkTraits
included-build dependency, and test files that are not present in the current
checkout.

Rule: use the plan for literal tablet requirements only after checking current
source. Do not use it as authority for current package names, dependency shape,
or test coverage.

### `docs/superpowers/specs/2026-07-03-tablet-network-design.md`

Status: Historical requirement source.

Rule: this file remains useful for literal tablet behavior such as price `150`,
hotbar slots `0-8`, chat length `120`, meeting cooldown `60s`, default vote
time `100s`, suspect reveal `45s/5s`, tablet highlight blue `0x1B8AE5`, and
suspect orange `0xFF8C00`. Current source owns the architecture; the spec owns
the original user-facing requirements.

## Watch-Only Notes

### `role/economy/`

Status: Watch-Only shared glue.

Rule: `RoleEconomyRules` may reference each role's own rules for money
eligibility and task rewards. It must not accumulate role-specific shop,
cooldown, item, highlight, or state behavior.

### `component/`

Status: Watch-Only state layer.

Rule: components should remain domain-specific state containers with sync/NBT
semantics. Do not move role predicates, shop entries, or packet authority into
components just because state is already nearby.

### `network/SparkStrengthPackets.java`

Status: Watch-Only packet registry and dispatcher.

Rule: keep packet registration and service dispatch here. Server action
validation belongs in the owning service Module, not in packet receiver lambdas.

### `mixin/`

Status: Watch-Only adapter layer.

Rule: mixins may adapt external seams and delegate. Do not let mixins become
the owner of domain behavior when changing Professor, Veteran, Noisemaker,
Criminologist, or NoellesRoles hidden-equipment behavior.

### `src/test`

Status: Focused pure-rule coverage enabled.

Rule: keep automated tests focused on domain rules and contracts that run
without Minecraft server bootstrap. The initial Engineer coverage protects
capture candidate eligibility and lifetime-boundary ordering. Registry-, CCA-,
entity-, mixin-, and rendering-heavy behavior still requires compile/static/jar
or integration gates rather than fake unit tests.

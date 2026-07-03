# Tablet Network Design

Date: 2026-07-03
Project: SparkStrength

## Goal

Add a tablet network feature for three eligible good-side audiences:

- Wathe Vigilante (`wathe:vigilante`)
- NoellesRoles Corrupt Cop (`noellesroles:corrupt_cop`)
- SparkTraits active Impostor trait holders (`sparktraits:impostor`) who are treated as the requested "impostor-trait good players"

The feature gives these audiences access to money visibility, shop access, and the ability to buy the tablet item for 150 coins. Tablet runtime features are then based on actually holding the tablet in hotbar slots 0-8.

## Hard Requirements

- Money, shop access, and tablet purchase are granted only to the three eligible audiences above.
- Holding a tablet does not grant money visibility, shop access, or purchase rights by itself.
- Tablet possession is checked only in hotbar slots 0-8, matching Wathe shop insertion behavior.
- Player IDs shown in tablet UI are Minecraft in-game names, not UUIDs.
- Connection status must not leak death state. It only distinguishes in-game players from non-game/admin/spectator players.
- Tablet highlights must not use the instinct key.
- Tablet highlights match NoellesRoles Toxicologist behavior: automatic outline, gated by `localPlayer.canSee(target)`, so it does not highlight through walls.
- Suspect highlights are visible only to tablet holders, not global glowing state.
- Implementation must not change unrelated roles, unrelated traits, SparkTraits trait distribution, or existing NoellesRoles role logic.
- Any nontrivial code comment added for this feature is bilingual: English and Chinese.

## Dependencies

SparkStrength will explicitly depend on:

- TrainMurderMystery / Wathe
- NoellesRoles
- SparkFactionAPI
- SparkTraits

SparkTraits is required so SparkStrength can authoritatively check active `sparktraits:impostor` state. The dependency is narrow: SparkStrength reads the active trait state for eligibility only and does not alter SparkTraits rules.

## Eligibility Model

There are two separate concepts:

1. Purchase eligibility
2. Tablet feature authority

Purchase eligibility is role/trait based. A player is eligible if they are one of:

- Vigilante
- Corrupt Cop
- Active Impostor trait holder

Eligible players get:

- `CanSeeMoney` allowance
- shop entry access
- the 150 coin tablet shop item
- existing good-role style task money behavior: initial 0 coins and +50 coins per completed task

Tablet feature authority is item based. A player can use tablet features only if they physically hold the tablet in hotbar slots 0-8, with additional feature-specific checks below.

## Tablet Item

Register a new `sparkstrength:tablet` item.

Behavior:

- Right-click opens the tablet GUI.
- The item itself does not grant money/shop privileges.
- The server validates all privileged actions. Client UI state is treated as display only.

## GUI Structure

The tablet GUI has:

- top local clock, rendered from the user's local client time
- left tab list with four options:
  - Connections
  - Chat
  - Emergency Meeting
  - Suspects

The UI follows existing SparkStrength/Wathe screen patterns: compact game UI, player heads via existing skin drawing helpers, and no forced full-screen meeting takeover.

## Tab 1: Connections

Shows players who currently have a tablet in hotbar slots 0-8.

For each row:

- player avatar
- Minecraft in-game name
- border color:
  - green if the player is in the Wathe game
  - gray if the player is not in the Wathe game

Important privacy rule:

- Do not show alive/dead state here.
- A dead in-game player and an alive in-game player both use the in-game border.

## Tab 2: Chat

All tablet holders can participate in tablet chat, including players who are not alive or not in the current Wathe game.

Server rules:

- A message is accepted only if the sender has the tablet in hotbar slots 0-8.
- Blank messages are ignored.
- Messages are trimmed and capped at 120 characters.
- The most recent 50 chat messages are stored in world tablet state and synced to tablet clients.

## Tab 3: Emergency Meeting

Meeting controls are available only to eligible meeting participants.

A meeting participant is:

- in the Wathe game
- alive
- holding the tablet in hotbar slots 0-8

Calling a meeting:

- Any meeting participant can call a meeting.
- There is one global meeting cooldown.
- Cooldown is 60 seconds.
- A new meeting cannot start while another meeting is active.
- After a meeting ends, the global cooldown starts.

During a meeting:

- The meeting lasts up to 100 seconds.
- Voting happens inside the tablet UI only.
- Players are not teleported, frozen, or forced into a separate screen.
- Eligible meeting participants can vote, change vote, abstain, and confirm.
- Before confirming, a participant may change vote or abstain.
- Confirming locks that participant's final choice.
- The meeting ends when the timer expires or all current meeting participants have confirmed.

Vote targets:

- All current Wathe in-game participants are shown as meeting rows.
- Players already on the suspect list are not selectable as vote targets.
- Abstain is always available.

Meeting result:

- If all votes are abstain or no valid votes exist, no suspect is added.
- If the highest valid vote count is tied, no suspect is added.
- If exactly one player has the highest valid vote count, that player becomes a suspect.
- A suspect cannot be selected in later emergency meetings while they remain on the suspect list.

## Tab 4: Suspects

Shows all current suspects.

For each row:

- avatar
- Minecraft in-game name
- current continuous removal approval count
- whether the local player has approved removal

Removal voting:

- This is not a formal timed meeting vote.
- It is continuous and can happen at any time from the suspects tab.
- The electorate is the current set of alive Wathe in-game players who hold the tablet in hotbar slots 0-8.
- Each electorate member may approve removal for a suspect or cancel their approval.
- When approvals reach at least two thirds of the current electorate, the suspect is removed.

After removal:

- The player stops receiving suspect orange highlights.
- The player becomes selectable in future emergency meetings again.
- Existing removal votes for that suspect are cleared.

## Highlights

There are two tablet-related highlights.

### Tablet Holder Highlight

Viewers:

- clients whose local player holds the tablet in hotbar slots 0-8

Targets:

- players who hold the tablet in hotbar slots 0-8
- are alive in the Wathe game

Rendering:

- use Vigilante role color (`0x1B8AE5`)
- no instinct key
- automatic outline result
- only if the local player can see the target, matching NoellesRoles Toxicologist behavior

### Suspect Highlight

Viewers:

- clients whose local player holds the tablet in hotbar slots 0-8

Targets:

- current suspects who are alive in the Wathe game

Rendering:

- orange highlight
- 5 seconds on every 45 seconds
- no instinct key
- automatic outline result
- only if the local player can see the target
- not implemented as global glowing, so non-tablet players do not see it

## Data Ownership

Add tablet-specific components instead of expanding existing Criminologist or general role-enhancement state.

Server-owned world state:

- chat history
- active meeting state
- global meeting cooldown end time
- suspect list
- suspect removal approvals

Player state:

- no new persistent tablet player component is required for the initial implementation
- meeting votes, confirmations, chat records, suspect lists, and removal approvals are keyed in world state
- role/trait eligibility is evaluated from Wathe, NoellesRoles, and SparkTraits sources instead of cached as a tablet-specific fact

Server state is authoritative. Client packets request actions; server validates tablet possession, game membership, alive state, cooldowns, vote locks, and thresholds.

## Network Packets

Add dedicated tablet packets under a tablet namespace/package.

C2S:

- request tablet state refresh
- send chat message
- call meeting
- cast/change meeting vote
- confirm meeting vote
- approve/cancel suspect removal

S2C:

- open tablet screen
- sync tablet snapshot
- append chat message or resync chat history
- meeting state update
- suspect list update

Packets use compact IDs and preserve existing SparkStrength packet style.

## Reset And Lifecycle

Tablet world state resets when the Wathe game/round lifecycle resets or finalizes:

- active meeting cleared
- suspect list cleared
- removal approvals cleared
- meeting cooldown cleared
- chat history cleared

Item possession itself follows normal inventory rules and is not forcibly removed by this feature unless existing shop/round cleanup already removes role items.

## Testing Plan

Unit or game-test coverage focuses on server rules:

- only Vigilante, Corrupt Cop, and active Impostor trait holders get money/shop/tablet purchase access
- generic tablet holders do not get money/shop access
- hotbar-only tablet possession check
- connection tab does not expose death state
- meeting cannot start during an active meeting
- global cooldown blocks meeting calls for 60 seconds after a meeting ends
- vote change before confirm works
- confirm locks the vote
- tied highest vote produces no suspect
- all-abstain vote produces no suspect
- unique highest vote adds one suspect
- current suspects are excluded from future meeting target choices
- continuous removal reaches two-thirds threshold and removes the suspect
- removed suspects can become future suspects again

Manual verification covers:

- GUI tabs and local clock
- avatar/name rendering
- tablet chat across multiple tablet holders
- disabled UI for dead/non-game players in meeting and suspect removal controls
- automatic non-keybind highlights in line of sight only
- non-tablet clients do not see suspect highlights

## Non-Goals

- Do not add a new instinct keybind.
- Do not create forced meeting teleport/freeze behavior.
- Do not show death state in the connections tab.
- Do not give tablet holders money/shop access merely because they hold a tablet.
- Do not broaden tablet purchase eligibility to unrelated good roles.
- Do not rewrite existing Criminologist, Toxicologist, Attendant, or Corrupt Cop features unless a narrow integration point requires it.

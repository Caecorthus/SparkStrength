package annina.sparkstrength.role.detective;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent;
import annina.sparkstrength.component.detective.DetectiveCaseWorldComponent;
import annina.sparkstrength.component.partyanimal.PartygoerCorpseComponent;
import annina.sparkstrength.network.detective.DetectiveInterrogationResultS2CPacket;
import annina.sparkstrength.network.detective.OpenDetectiveFolderS2CPacket;
import annina.sparkstrength.network.detective.OpenDetectiveInterrogationS2CPacket;
import annina.sparkstrength.record.AchievementRecords;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server authority for the detective's magnifier and case folder: starter kit, crime-scene snapshots, investigations
 * and folder edits.
 * 侦探放大镜与文件夹的服务端权威逻辑：开局道具、案发现场快照、调查与文件夹编辑。
 *
 * <p>Every entry point re-checks the holder's role at use time (Wraith transitions change roles without
 * RoleAssigned). The magnifier cooldown is only ever set here, as a server-side vanilla item cooldown, so
 * SparkTraits Fast Hands shortens it without SparkStrength referencing it.
 * 每个入口都在使用时重新校验身份（怨灵转换不会触发 RoleAssigned）。放大镜冷却只在这里以服务端原版物品冷却设置，
 * SparkTraits 快手特质会自动缩短它，SparkStrength 无需引用其内部实现。</p>
 */
public final class DetectiveCaseService {
    // PlayerBodyEntity.getPlayerUuid() returns this Wathe sentinel while the owner is unset.
    // Wathe 尸体未设置主人时 getPlayerUuid() 返回的占位 UUID。
    private static final UUID UNSET_BODY_OWNER = UUID.fromString("25adae11-cd98-48f4-990b-9fe1b2ee0886");
    private static final Identifier MAGNIFIER_ACTION_ID = SparkStrength.id("detective_magnifier");
    private static final String MESSAGE_PREFIX = "message.sparkstrength.detective.";
    // Minimum gap between the defensive pre-open folder syncs of one player. 同一玩家打开前保险同步的最小间隔。
    private static final int OPEN_SYNC_MIN_INTERVAL_TICKS = 10;
    private static boolean registered;

    private DetectiveCaseService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerEntityEvents.ENTITY_LOAD.register(DetectiveCaseService::captureCrimeScene);
        ServerTickEvents.END_SERVER_TICK.register(DetectiveCaseService::flushFolderSyncs);
    }

    /**
     * RoleAssigned hook. A non-detective loses the kit and the case folder contents; a detective assigned mid-round
     * (status ACTIVE) gets the kit. Round-start detectives are handled by {@link #grantStarterKits} because SparkTraits
     * Conscience compensation can still rewrite roles after RoleAssigned during initialization.
     * RoleAssigned 钩子：非侦探收回道具并清空文件夹；局中（ACTIVE）成为侦探时发放道具。开局侦探由 grantStarterKits
     * 发放，因为初始化期间 SparkTraits 良心补偿仍可能在 RoleAssigned 之后改写身份。
     */
    public static void assignForRole(ServerPlayerEntity player, Role role) {
        if (!DetectiveRules.isDetective(role)) {
            removeKit(player);
            DetectiveCasePlayerComponent.KEY.get(player).clearAll();
            return;
        }
        if (GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
            grantKit(player);
        }
    }

    /**
     * Round-start grant from {@code GameEvents.ON_FINISH_INITIALIZE}, once every role (incl. trait compensation) is final.
     * 开局发放（ON_FINISH_INITIALIZE），此时所有身份（含天赋补偿）均已确定。
     */
    public static void grantStarterKits(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (game.hasAnyRole(player) && DetectiveRules.isDetective(game.getRole(player))) {
                grantKit(player);
            }
        }
    }

    /** Clears crime-scene snapshots only; the suspect-limit setting survives. 只清空案发快照，调查上限设置保留。 */
    public static void clearRoundState(ServerWorld world) {
        DetectiveCaseWorldComponent.KEY.get(world).clearRoundState();
    }

    /**
     * Server half of {@code MagnifierItem.useOnEntity}: right-clicking a corpse, or a NoellesRoles Morphling lying in
     * corpse mode, files the case. Filing is free and never looks at the cooldown. Living players are interrogated
     * remotely from the roster instead ({@link #handleInterrogate}); clicking one falls through to {@code use()}.
     * MagnifierItem.useOnEntity 的服务端部分：右键尸体，或处于尸体模式的 NoellesRoles 变形者，记录命案；记录免费，从不读取
     * 冷却。活人改为在名单中远程审问（handleInterrogate）；右键活人会落到 use()。
     */
    public static void useMagnifier(ServerPlayerEntity user, ItemStack stack, LivingEntity target, Hand hand) {
        if (user == null || target == null || !canUseDetectiveItems(user)) {
            return;
        }
        if (target instanceof PlayerBodyEntity body) {
            // Scavenger-hidden bodies report canHit()=false on both sides (NoellesRoles common mixin), so no normal
            // client can target them; a forged interact packet is treated like clicking air.
            // 清道夫隐藏的尸体在双端 canHit() 均为 false（NoellesRoles 通用 mixin），正常客户端无法选中；伪造的交互包视同对空气右键。
            if (!body.canHit()) {
                return;
            }
            fileCase(user, body);
        } else if (target instanceof ServerPlayerEntity morphling
                && morphling != user
                && GameFunctions.isPlayerPlayingAndAlive(morphling)
                && !morphling.isSpectator()
                && !morphling.isInvisible()
                && MorphlingPlayerComponent.KEY.get(morphling).corpseMode) {
            fileMorphlingCorpse(user, morphling);
        }
    }

    /**
     * Whether a magnifier right-click on {@code entity} files a case: a corpse, or a Morphling in corpse mode (synced
     * to every client, so both sides agree). Anything else falls through to {@code use()}, which opens the roster.
     * 放大镜右键该实体是否记录命案：尸体，或处于尸体模式的变形者（该状态同步给所有客户端，两端判断一致）。
     * 其余情况落到 use()，打开名单。
     */
    public static boolean filesAsCorpse(Entity entity) {
        return entity instanceof PlayerBodyEntity
                || entity instanceof PlayerEntity player && MorphlingPlayerComponent.KEY.get(player).corpseMode;
    }

    /** Case folder item. / 文件夹道具。 */
    public static void openFolder(ServerPlayerEntity player) {
        open(player, DetectiveCaseRules.DetectiveScreen.FOLDER);
    }

    /** Magnifier {@code use()}: the remote interrogation roster. / 放大镜 use()：远程审问名单。 */
    public static void openInterrogation(ServerPlayerEntity player) {
        open(player, DetectiveCaseRules.DetectiveScreen.INTERROGATION);
    }

    /**
     * Syncs the folder first so the screen never opens on stale data, then asks the client to open it. Every server
     * change already syncs (directly or via the per-tick flush), so this sync is a safety net and is throttled: a flood
     * of use packets cannot force a full-folder sync each time. The open packet itself is always sent.
     * 先同步文件夹，保证界面不会以过期数据打开，再通知客户端打开。服务端的每次修改本身都会同步（直接或经每 tick 合并），
     * 这里只是保险，因此做节流：大量使用包无法每次都强制完整同步。打开包始终发送。
     */
    private static void open(ServerPlayerEntity player, DetectiveCaseRules.DetectiveScreen screen) {
        if (player == null || !canUseDetectiveItems(player)) {
            return;
        }
        DetectiveCasePlayerComponent folder = DetectiveCasePlayerComponent.KEY.get(player);
        if (folder.isDirty()) {
            // Pending edits (e.g. notes saved on close this tick) must reach the client before the screen reopens, or it
            // would load and later re-save stale text. Defer the open to the end-of-tick flush so it still follows the
            // sync while edits stay coalesced to one sync per tick.
            // 待发送的修改（如本 tick 关闭时保存的笔记）必须先于重新打开送达客户端，否则会载入并回存旧文本。
            // 把打开推迟到 tick 末尾的合并同步之后，既保证顺序，又保持每 tick 最多一次同步。
            folder.requestOpenAfterFlush(screen);
            return;
        }
        if (folder.claimOpenSync(player.getServer().getTicks(), OPEN_SYNC_MIN_INTERVAL_TICKS)) {
            folder.sync();
        }
        sendOpen(player, screen);
    }

    private static void sendOpen(ServerPlayerEntity player, DetectiveCaseRules.DetectiveScreen screen) {
        CustomPayload payload = screen == DetectiveCaseRules.DetectiveScreen.INTERROGATION
                ? new OpenDetectiveInterrogationS2CPacket()
                : new OpenDetectiveFolderS2CPacket();
        if (ServerPlayNetworking.canSend(player, payload.getId())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void handleSelectCase(ServerPlayerEntity player, UUID caseId) {
        DetectiveCasePlayerComponent folder = editableFolder(player, caseId);
        if (folder != null && folder.selectCase(caseId)) {
            folder.markDirty();
        }
    }

    public static void handleUpdateNotes(ServerPlayerEntity player, UUID caseId, String notes) {
        DetectiveCasePlayerComponent folder = editableFolder(player, caseId);
        if (folder != null && folder.setNotes(caseId, DetectiveCaseRules.sanitizeNotes(notes))) {
            folder.markDirty();
        }
    }

    /**
     * Empty clears the guess; a present id must be a registered, non-special Wathe role, otherwise the request is ignored.
     * 空值清除推测；非空时必须是已注册且非特殊的 Wathe 角色，否则忽略请求。
     */
    public static void handleSetKillerGuess(ServerPlayerEntity player, UUID caseId, Optional<Identifier> roleId) {
        DetectiveCasePlayerComponent folder = editableFolder(player, caseId);
        if (folder == null) {
            return;
        }
        Identifier guess = null;
        if (roleId != null && roleId.isPresent()) {
            Role role = findGuessableRole(roleId.get());
            if (role == null) {
                return;
            }
            guess = role.identifier();
        }
        if (folder.setKillerGuess(caseId, guess)) {
            folder.markDirty();
        }
    }

    /**
     * Empty clears the presumed killer; a present uuid must be a participant of the current round (Wathe game profiles,
     * filled on role assignment and cleared with the role map) and not the detective themself, otherwise it is ignored.
     * 空值清除假定凶手；非空时必须是本局参与者（Wathe 的 gameProfiles，分配身份时写入、随身份表一起清空）
     * 且不能是侦探本人，否则忽略请求。
     */
    public static void handleSetPresumedKiller(ServerPlayerEntity player, UUID caseId, Optional<UUID> playerUuid) {
        DetectiveCasePlayerComponent folder = editableFolder(player, caseId);
        if (folder == null) {
            return;
        }
        UUID presumed = null;
        if (playerUuid != null && playerUuid.isPresent()) {
            presumed = playerUuid.get();
            if (presumed.equals(player.getUuid())
                    || !GameWorldComponent.KEY.get(player.getWorld()).getGameProfiles().containsKey(presumed)) {
                return;
            }
        }
        if (folder.setPresumedKiller(caseId, presumed)) {
            folder.markDirty();
        }
    }

    /**
     * Sends at most one coalesced folder sync per player per tick for client-driven edits.
     * 客户端触发的修改每名玩家每 tick 最多合并同步一次。
     */
    private static void flushFolderSyncs(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            DetectiveCasePlayerComponent folder = DetectiveCasePlayerComponent.KEY.get(player);
            folder.flushIfDirty();
            // A deferred open goes out right after the flush, and only if the player may still use the folder.
            // 延迟的打开请求紧随合并同步发送，且仅在玩家仍可使用文件夹时发送。
            DetectiveCaseRules.DetectiveScreen deferred = folder.consumeOpenAfterFlush();
            if (deferred != null && canUseDetectiveItems(player)) {
                sendOpen(player, deferred);
            }
        }
    }

    public static int getSuspectLimit(ServerWorld world) {
        return DetectiveCaseWorldComponent.KEY.get(world).getSuspectLimit();
    }

    /**
     * Admin setting: stored in every ServerWorld (the player component reads its own world's value), then every
     * online folder is re-synced so open HUDs and screens show the new limit.
     * 管理员设置：写入每个 ServerWorld（玩家组件读取所在世界的值），再重新同步所有在线玩家的文件夹，使 HUD 与界面即时更新。
     */
    public static void setSuspectLimit(MinecraftServer server, int limit) {
        int clamped = DetectiveCaseRules.clampSuspectLimit(limit);
        for (ServerWorld world : server.getWorlds()) {
            DetectiveCaseWorldComponent.KEY.get(world).setSuspectLimit(clamped);
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            DetectiveCasePlayerComponent.KEY.get(player).sync();
        }
    }

    public static boolean isDetectiveKit(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.isOf(SparkStrengthItems.magnifier()) || stack.isOf(SparkStrengthItems.caseFolder()));
    }

    /**
     * ENTITY_LOAD fires synchronously inside {@code World.spawnEntity}, so for a Wathe kill the victim is already marked
     * dead and still stands at the death point, while the body may sit up to 1 block away (and can be dragged later).
     * Real kills and fake bodies (SparkTraits Depression / Last Stand, NoellesRoles Jester) are snapshotted the same
     * way so the magnifier never reveals a fake death. Chunk reloads re-fire the event; the first snapshot per body wins.
     * ENTITY_LOAD 在 spawnEntity 内同步触发：Wathe 击杀时受害者已被标记死亡且仍站在死亡点，尸体可能偏移至多 1 格
     * （之后还可能被拖动）。真实击杀与假尸体（SparkTraits 抑郁/最后一搏、NoellesRoles 小丑）统一记录，放大镜不会暴露假死。
     * 区块重载会再次触发该事件；每具尸体只保留首次快照。
     *
     * <p>A Partygoer corpse is no crime scene (owner 2026-10-21): its marker is set before it spawns, so it is skipped
     * here and the magnifier answers "no clues" on it.
     * 派对客尸体不是案发现场（所有者 2026-10-21）：其标记在生成前写入，因此这里直接跳过，放大镜对它回应“没有线索”。</p>
     */
    private static void captureCrimeScene(Entity entity, ServerWorld world) {
        if (!(entity instanceof PlayerBodyEntity body) || PartygoerCorpseComponent.isPartygoerCorpse(body)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        UUID ownerUuid = body.getPlayerUuid();
        if (!game.isRunning() || ownerUuid == null || UNSET_BODY_OWNER.equals(ownerUuid)) {
            return;
        }
        DetectiveCaseWorldComponent scenes = DetectiveCaseWorldComponent.KEY.get(world);
        if (scenes.get(body.getUuid()) != null) {
            return;
        }

        ServerPlayerEntity owner = onlineIn(world, ownerUuid);
        boolean ownerDead = owner != null && game.isPlayerDead(ownerUuid);
        // Measure from the dead owner's position, never from the movable body.
        // 以已死亡主人的位置为测距原点，而不是可被拖动的尸体。
        Vec3d origin = ownerDead ? owner.getPos() : body.getPos();

        LinkedHashMap<UUID, Vec3d> positions = alivePositions(world, game);
        if (owner != null) {
            // A fake-body owner is still alive and keeps their live position.
            // 假尸体的主人仍存活，记录其实时位置。
            positions.put(ownerUuid, ownerDead ? origin : owner.getPos());
        }
        scenes.captureIfAbsent(body.getUuid(), new DetectiveCaseWorldComponent.CrimeSceneSnapshot(ownerUuid, origin, positions));
    }

    /**
     * Living, non-spectating participants. Spectators who are not dead (a Taotie-swallowed player, a fake death's few
     * seconds) are left out and answer "not present" like the dead: a swallowed player's position would point at the
     * Taotie, and a distance would tell the detective they are alive.
     * 存活且非旁观的参与者。未死亡的旁观者（被饕餮吞下的玩家、假死的几秒）不计入，与死者一样回答“不在场”：被吞玩家的位置
     * 会指向饕餮，给出距离也会让侦探得知其仍然存活。
     */
    private static LinkedHashMap<UUID, Vec3d> alivePositions(ServerWorld world, GameWorldComponent game) {
        LinkedHashMap<UUID, Vec3d> positions = new LinkedHashMap<>();
        for (UUID uuid : game.getAllAlivePlayers()) {
            ServerPlayerEntity player = onlineIn(world, uuid);
            if (player != null && GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator()) {
                positions.put(uuid, player.getPos());
            }
        }
        return positions;
    }

    private static void fileCase(ServerPlayerEntity user, PlayerBodyEntity body) {
        ServerWorld world = user.getServerWorld();
        fileCase(user, body.getUuid(), () -> DetectiveIdentityResolver.resolveBody(world, body));
    }

    /**
     * A NoellesRoles Morphling lying in corpse mode is filed exactly like a real body (every real body in a round has a
     * snapshot, so any other answer would expose the fake). Its scene is captured lazily on the first click: origin =
     * the Morphling's position, positions = every living participant including the Morphling. The case is keyed
     * server-side by the Morphling and its apparent identity (the synced id is random), so a new disguise is a new
     * case. Free, like filing a body.
     * 尸体模式的 NoellesRoles 变形者按真实尸体完全相同的方式归档（一局内每具真实尸体都有快照，其他任何回应都会暴露假尸体）。
     * 首次点击时才记录现场：原点为变形者位置，位置表为所有存活参与者（含变形者本人）。命案在服务端按变形者与其表面身份区分
     * （同步出去的是随机 ID），换一种伪装就是新命案。与记录尸体一样不消耗冷却。
     */
    private static void fileMorphlingCorpse(ServerPlayerEntity user, ServerPlayerEntity morphling) {
        ServerWorld world = user.getServerWorld();
        DetectiveIdentityResolver.DisplayedIdentity victim = DetectiveIdentityResolver.resolveAppearance(morphling);
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        DetectiveCaseWorldComponent scenes = DetectiveCaseWorldComponent.KEY.get(world);
        // The case id is synced to the detective, so it is random rather than a hash of the real uuid.
        // 命案 ID 会同步给侦探，因此使用随机值而不是真实 UUID 的哈希。
        UUID caseId = scenes.morphlingCorpseCaseId(morphling.getUuid(), victim.displayUuid());
        if (game.isRunning() && scenes.get(caseId) == null) {
            Vec3d origin = morphling.getPos();
            LinkedHashMap<UUID, Vec3d> positions = alivePositions(world, game);
            positions.put(morphling.getUuid(), origin);
            scenes.captureIfAbsent(caseId,
                    new DetectiveCaseWorldComponent.CrimeSceneSnapshot(morphling.getUuid(), origin, positions));
        }
        fileCase(user, caseId, () -> victim);
    }

    private static void fileCase(ServerPlayerEntity user, UUID caseId,
                                 Supplier<DetectiveIdentityResolver.DisplayedIdentity> victimIdentity) {
        ServerWorld world = user.getServerWorld();
        if (DetectiveCaseWorldComponent.KEY.get(world).get(caseId) == null) {
            sendActionBar(user, "no_clues");
            return;
        }

        DetectiveCasePlayerComponent folder = DetectiveCasePlayerComponent.KEY.get(user);
        DetectiveCasePlayerComponent.DetectiveCase existing = folder.findCase(caseId);
        if (existing != null) {
            // Re-selecting is spammable by alternating bodies, so it is coalesced like the folder edits.
            // 交替点击尸体可以刷重新选中，因此与文件夹编辑一样合并同步。
            if (folder.selectCase(caseId)) {
                folder.markDirty();
            }
            sendActionBar(user, "case_selected", Text.literal(existing.getVictimName()));
            return;
        }
        if (folder.getCases().size() >= DetectiveCaseRules.MAX_CASES) {
            sendActionBar(user, "folder_full");
            return;
        }

        DetectiveIdentityResolver.DisplayedIdentity victim = victimIdentity.get();
        if (!folder.addCase(caseId, victim.displayUuid(), victim.displayName())) {
            sendActionBar(user, "folder_full");
            return;
        }
        folder.selectCase(caseId);
        folder.sync();
        sendActionBar(user, "case_recorded", Text.literal(victim.displayName()));
    }

    /**
     * Remote interrogation from the magnifier roster ({@code InterrogateDetectiveSuspectC2SPacket}). The client names a
     * participant by real uuid and the answer comes from the selected case's crime-scene snapshot, so it never depends
     * on where the target is now or what they look like: a living player's disguise cannot fool it, and the dead
     * answer like the living (owner 2026-10-25: the roster lists every participant and never shows who died). No
     * kill-time position, being the case's victim (real or displayed, so a disguised or fake body holds up), or a
     * player-affect policy shielding the target all answer "not present", which spends the cooldown like a clue does,
     * so the roster cannot be probed for free. The folder keeps that answer per case, so asking again repeats it for
     * free.
     * 放大镜名单中的远程审问。客户端以真实 UUID 指定参与者，回应只取自当前命案的现场快照，与目标此刻的位置、外观都无关：
     * 活人的伪装骗不到它，死者与活人一样回应（所有者 2026-10-25：名单列出所有参与者，从不显示谁已死亡）。没有案发时位置、
     * 本案受害人（真实或显示的均算，伪装尸体与假尸体因此不会露馅）、或受玩家影响策略保护的目标都回答“不在场”，与记录线索
     * 一样消耗冷却，因此名单无法被免费试探。文件夹按命案保存该回应，再次询问时免费重复。
     */
    public static void handleInterrogate(ServerPlayerEntity user, UUID targetUuid) {
        if (user == null || targetUuid == null || !canUseDetectiveItems(user) || !isHoldingMagnifier(user)) {
            return;
        }
        ServerWorld world = user.getServerWorld();
        // Only a current-round participant other than the detective (Wathe game profiles, as for the presumed killer);
        // anything else is a forged request. 只接受本局参与者且不是侦探本人（与假定凶手相同取 Wathe 玩家档案），其余视为伪造请求。
        if (targetUuid.equals(user.getUuid())
                || !GameWorldComponent.KEY.get(world).getGameProfiles().containsKey(targetUuid)) {
            return;
        }

        DetectiveCasePlayerComponent folder = DetectiveCasePlayerComponent.KEY.get(user);
        DetectiveCasePlayerComponent.DetectiveCase selected = folder.getSelectedCase();
        int limit = getSuspectLimit(world);
        // NO_CASE and LIMIT_REACHED depend only on the detective's own folder, so they answer even while the magnifier
        // cools down (the clue that fills a case always starts it); target-dependent outcomes stay behind the cooldown.
        // “无命案”与“已达上限”只取决于侦探自己的文件夹，冷却中也会提示（填满命案的那条线索必然触发冷却）；
        // 与目标相关的结果仍需等待冷却结束。
        DetectiveCaseRules.InvestigationOutcome folderOutcome = DetectiveCaseRules.decideInvestigation(
                selected != null, selected == null ? 0 : selected.getSuspects().size(), limit, false, true);
        if (selected == null || folderOutcome != DetectiveCaseRules.InvestigationOutcome.RECORDED) {
            answer(user, selected == null ? null : selected.getCaseId(), targetUuid, folderOutcome, 0);
            return;
        }
        Item magnifier = SparkStrengthItems.magnifier();
        if (user.getItemCooldownManager().isCoolingDown(magnifier)) {
            // The roster greys out while cooling down; a request racing the cooldown is dropped.
            // 冷却期间名单不可点击；与冷却赛跑的请求直接丢弃。
            return;
        }

        UUID caseId = selected.getCaseId();
        if (folder.isAbsent(caseId, targetUuid)) {
            // The folder already holds this answer: repeating it is free and never re-evaluated, so a target that was
            // shielded at the first ask cannot later reveal the shield. 文件夹已有此回应：重复回答免费且不重新判定，
            // 首次询问时受保护的目标之后也不会暴露保护。
            answer(user, caseId, targetUuid, DetectiveCaseRules.InvestigationOutcome.NOT_PRESENT, 0);
            return;
        }
        // The clue is keyed on the uuid the detective picked, which is also its display uuid.
        // 线索以侦探所选的 UUID 为键，它同时也是显示 UUID。
        boolean alreadyRecorded = folder.hasSuspectPair(caseId, targetUuid, false);
        // A vanished snapshot reads as NOT_PRESENT. 快照缺失时按“不在场”处理。
        DetectiveCaseWorldComponent.CrimeSceneSnapshot snapshot = DetectiveCaseWorldComponent.KEY.get(world).get(caseId);
        Vec3d killTimePosition = snapshot == null ? null : snapshot.positions().get(targetUuid);
        // Player-affect policies (a Rift Gate occupant, an active Wraith) judge online players only.
        // 玩家影响策略（裂隙门内、冤魂状态）只能判断在线玩家。
        ServerPlayerEntity suspect = onlineIn(world, targetUuid);
        boolean shielded = suspect != null && !SparkFactionCompat.canAffectPlayer(user, suspect, MAGNIFIER_ACTION_ID);
        boolean caseVictim = snapshot != null
                && (targetUuid.equals(snapshot.victimUuid()) || targetUuid.equals(selected.getVictimDisplayUuid()));
        boolean present = snapshot != null && DetectiveCaseRules.answersWithDistance(
                killTimePosition != null, caseVictim, shielded);

        DetectiveCaseRules.InvestigationOutcome outcome = DetectiveCaseRules.decideInvestigation(
                true, selected.getSuspects().size(), limit, alreadyRecorded, present);
        if (outcome != DetectiveCaseRules.InvestigationOutcome.RECORDED || snapshot == null || killTimePosition == null) {
            if (DetectiveCaseRules.startsCooldown(outcome)) {
                user.getItemCooldownManager().set(magnifier, DetectiveCaseRules.MAGNIFIER_COOLDOWN_TICKS);
            }
            boolean absentRecorded = outcome == DetectiveCaseRules.InvestigationOutcome.NOT_PRESENT
                    && folder.markAbsent(caseId, targetUuid);
            answer(user, caseId, targetUuid, outcome, 0);
            if (absentRecorded) {
                folder.sync();
            }
            return;
        }

        // 3D distance from the victim's death point to the suspect's kill-time position.
        // 从受害者死亡点到嫌疑人案发时位置的三维距离。
        int distanceBlocks = DetectiveCaseRules.roundDistanceBlocks(snapshot.origin().distanceTo(killTimePosition));
        // realUuid stays server-side (NBT only); the sync never writes it.
        // realUuid 仅留在服务端（只写入 NBT），同步时绝不写出。
        DetectiveCasePlayerComponent.SuspectClue clue = new DetectiveCasePlayerComponent.SuspectClue(
                targetUuid, targetUuid, DetectiveIdentityResolver.nameOf(world.getServer(), world, targetUuid, null),
                false, distanceBlocks);
        if (!folder.addSuspect(caseId, clue)) {
            return;
        }
        user.getItemCooldownManager().set(magnifier, DetectiveCaseRules.MAGNIFIER_COOLDOWN_TICKS);
        answer(user, caseId, targetUuid, outcome, distanceBlocks);
        folder.sync();
        // Achievement record: the real suspect and the snapshot's real victim. Wathe records online players only.
        // 成就记录：真实嫌疑人与快照中的真实死者；Wathe 只能记录在线玩家。
        if (suspect != null) {
            AchievementRecords.detectiveInterrogate(user, suspect, snapshot.victimUuid());
        }
    }

    /**
     * Action bar line (still readable once the roster closes) plus the result packet for the open roster.
     * 动作栏提示（关闭名单后仍可看到）以及发给打开中名单的结果包。
     */
    private static void answer(ServerPlayerEntity user, @Nullable UUID caseId, UUID targetUuid,
                               DetectiveCaseRules.InvestigationOutcome outcome, int distanceBlocks) {
        ServerWorld world = user.getServerWorld();
        Text name = Text.literal(DetectiveIdentityResolver.nameOf(world.getServer(), world, targetUuid, null));
        switch (outcome) {
            case NO_CASE -> sendActionBar(user, "no_case");
            case LIMIT_REACHED -> sendActionBar(user, "enough_clues");
            case ALREADY_RECORDED -> sendActionBar(user, "already_recorded");
            case NOT_PRESENT -> sendActionBar(user, "not_present", name);
            case RECORDED -> sendActionBar(user, "clue", name, distanceBlocks);
        }
        if (ServerPlayNetworking.canSend(user, DetectiveInterrogationResultS2CPacket.ID)) {
            ServerPlayNetworking.send(user,
                    new DetectiveInterrogationResultS2CPacket(caseId, targetUuid, outcome, distanceBlocks));
        }
    }

    private static @Nullable DetectiveCasePlayerComponent editableFolder(ServerPlayerEntity player, UUID caseId) {
        if (player == null || caseId == null || !canUseDetectiveItems(player)) {
            return null;
        }
        DetectiveCasePlayerComponent folder = DetectiveCasePlayerComponent.KEY.get(player);
        return folder.findCase(caseId) == null ? null : folder;
    }

    private static boolean canUseDetectiveItems(ServerPlayerEntity player) {
        return DetectiveRules.isDetective(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator();
    }

    /** The roster is opened from the magnifier, so asking still needs it in hand. / 名单由放大镜打开，审问时仍须手持放大镜。 */
    private static boolean isHoldingMagnifier(ServerPlayerEntity player) {
        Item magnifier = SparkStrengthItems.magnifier();
        return player.getMainHandStack().isOf(magnifier) || player.getOffHandStack().isOf(magnifier);
    }

    private static @Nullable Role findGuessableRole(Identifier roleId) {
        for (Role special : WatheRoles.SPECIAL_ROLES) {
            if (special != null && roleId.equals(special.identifier())) {
                return null;
            }
        }
        for (Role role : WatheRoles.ROLES) {
            if (role != null && roleId.equals(role.identifier())) {
                return role;
            }
        }
        return null;
    }

    private static void grantKit(ServerPlayerEntity player) {
        // RoleAssigned can fire more than once per round; scan every slot so the kit never duplicates.
        // RoleAssigned 每局可能触发多次；扫描所有槽位，避免重复发放。
        giveIfMissing(player, SparkStrengthItems.magnifier());
        giveIfMissing(player, SparkStrengthItems.caseFolder());
    }

    private static void giveIfMissing(ServerPlayerEntity player, Item item) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(item)) {
                return;
            }
        }
        player.giveItemStack(new ItemStack(item));
    }

    private static void removeKit(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (isDetectiveKit(player.getInventory().getStack(slot))) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
            }
        }
        // Wathe returns a held cursor stack to the inventory on close, so clear it too.
        // Wathe 会在关闭界面时把光标上的物品放回背包，因此一并清除。
        ScreenHandler handler = player.currentScreenHandler;
        if (handler != null && isDetectiveKit(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
        }
    }

    private static @Nullable ServerPlayerEntity onlineIn(ServerWorld world, UUID uuid) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
        return player != null && player.getWorld() == world ? player : null;
    }

    private static void sendActionBar(ServerPlayerEntity player, String key, Object... args) {
        player.sendMessage(Text.translatable(MESSAGE_PREFIX + key, args), true);
    }
}

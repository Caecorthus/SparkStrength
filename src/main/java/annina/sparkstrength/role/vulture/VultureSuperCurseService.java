package annina.sparkstrength.role.vulture;

import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.compat.SparkTraitsVultureCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.component.vulture.VultureSuperCursePlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server authority for the Vulture's Super Curse and death scream. The client only reports a key press
 * ({@code VultureSuperCurseC2SPacket}); round, role, liveness, skill locks and cooldown are re-checked here. Both sounds
 * are pure flavour with no gameplay effect.
 * 秃鹫超级骂与死亡惨叫的服务端权威逻辑。客户端只上报按键（{@code VultureSuperCurseC2SPacket}）；回合、职业、存活、
 * 技能封锁与冷却都在这里重新校验。两种声音都只是氛围效果，不影响玩法。
 */
public final class VultureSuperCurseService {
    /** Curse sounds a death or round end must cut off. / 死亡或回合结束时需要切断的超级骂音效。 */
    private static final List<Identifier> CURSE_SOUND_IDS = List.of(
            SparkStrengthSounds.VULTURE_SUPER_CURSE_ID,
            SparkStrengthSounds.VULTURE_SUPER_CURSE_HELIUM_ID
    );
    /**
     * Per world: Vulture uuid -> world time their last curse is certainly over. Lets death and round end send stop
     * packets only when a curse may still be playing.
     * 按世界记录：秃鹫 UUID -> 其上一次超级骂必定结束的世界时间。死亡与回合结束只在超级骂可能仍在播放时才发停止包。
     */
    private static final Map<ServerWorld, Map<UUID, Long>> ACTIVE_CURSES = new IdentityHashMap<>();
    private static boolean registered;

    private VultureSuperCurseService() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Wathe keeps the victim's role through KillPlayer.AFTER. SparkTraits' own AFTER listener snapshots and then
        // wipes the victim's traits, so the scream reads round-end trait ids, which are right on either side of it.
        // Wathe 在 KillPlayer.AFTER 中仍保留受害者职业。SparkTraits 自己的 AFTER 监听会先快照再清空受害者词条，
        // 因此惨叫读取“本局最终词条”，无论在它之前还是之后执行都正确。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> onKilled(victim));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                endRound(serverWorld);
            }
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> ACTIVE_CURSES.remove(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE_CURSES.clear());
    }

    /**
     * RoleAssigned hook: a new Vulture waits 60 s for the first curse; any other role drops the state.
     * RoleAssigned 钩子：新秃鹫需等待 60 秒才能首次超级骂；其他职业清空状态。
     */
    public static void assignForRole(ServerPlayerEntity player, Role role) {
        VultureSuperCursePlayerComponent component = VultureSuperCursePlayerComponent.KEY.get(player);
        if (VultureSuperCurseRules.isVulture(role)) {
            component.initialize();
        } else {
            component.clear();
        }
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        VultureSuperCursePlayerComponent.KEY.get(player).clear();
    }

    /**
     * Handles a Super Curse request on the server thread. Rejected presses are silent, as with Sniff.
     * 在服务端主线程处理超级骂请求。被拒绝的按键保持静默，与嗅探一致。
     */
    public static void tryCurse(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !isActiveVulture(player)
                || isSkillLocked(player)) {
            return;
        }
        VultureSuperCursePlayerComponent component = VultureSuperCursePlayerComponent.KEY.get(player);
        if (!component.isReady()) {
            return;
        }

        // Live trait check; without SparkTraits this is false and the normal clip plays.
        // 实时词条判定；未安装 SparkTraits 时为 false，播放普通版本。
        boolean childish = SparkTraitsCompat.hasTrait(player, VultureSuperCurseRules.CHILDISH_TRAIT_ID);
        SoundEvent curse = childish
                ? SparkStrengthSounds.VULTURE_SUPER_CURSE_HELIUM
                : SparkStrengthSounds.VULTURE_SUPER_CURSE;
        // Entity-bound so the curse follows the Vulture; heard by everyone in range, the Vulture included.
        // 绑定实体，使超级骂跟随秃鹫移动；范围内所有人（包括秃鹫本人）都能听到。
        world.playSoundFromEntity(null, player, Registries.SOUND_EVENT.getEntry(curse), SoundCategory.PLAYERS,
                VultureSuperCurseRules.SOUND_VOLUME, 1.0F, world.getRandom().nextLong());
        component.startCooldown();
        ACTIVE_CURSES.computeIfAbsent(world, ignored -> new HashMap<>())
                .put(player.getUuid(), VultureSuperCurseRules.curseEndTick(world.getTime()));
    }

    /**
     * Alive, playing, in survival, not swallowed by the Taotie, and currently the Vulture.
     * 存活、参与对局、处于生存模式、未被饕餮吞噬，且当前职业为秃鹫。
     */
    private static boolean isActiveVulture(ServerPlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && GameFunctions.isPlayerAliveAndSurvival(player)
                && !SwallowedPlayerComponent.isPlayerSwallowed(player)
                && VultureSuperCurseRules.isVulture(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /**
     * Same role-skill locks as the skateboard: Engineer stun, SparkTraits role-skill block and a pending Last Stand.
     * 与滑板相同的职业技能封锁：工程师定身、SparkTraits 职业技能封锁与待决的背水一战。
     */
    private static boolean isSkillLocked(ServerPlayerEntity player) {
        return EngineerStunnedPlayerComponent.KEY.get(player).isStunned()
                || SparkTraitsDroneCompat.isRoleSkillBlocked(player)
                || SparkTraitsDroneCompat.isLastStandPending(player);
    }

    private static void onKilled(ServerPlayerEntity victim) {
        ServerWorld world = victim.getServerWorld();
        if (!VultureSuperCurseRules.isVulture(GameWorldComponent.KEY.get(world).getRole(victim))) {
            return;
        }
        // Cut the curse first: it is bound to the player entity and would otherwise follow the spectator around.
        // 先切断超级骂：它绑定在玩家实体上，否则会跟随旁观者继续播放。
        Map<UUID, Long> curses = ACTIVE_CURSES.get(world);
        Long curseEnd = curses == null ? null : curses.remove(victim.getUuid());
        if (curseEnd != null && VultureSuperCurseRules.isCurseAudible(curseEnd, world.getTime())) {
            stopCurseSounds(world);
        }

        boolean childish = VultureSuperCurseRules.isChildish(
                SparkTraitsVultureCompat.getRoundEndTraitIds(world, victim.getUuid()));
        SoundEvent scream = childish
                ? SparkStrengthSounds.VULTURE_DEATH_SCREAM_HELIUM
                : SparkStrengthSounds.VULTURE_DEATH_SCREAM;
        // One seed in the packet: every client picks the same helium clip. / 数据包只带一个种子：所有客户端选中同一段氦气惨叫。
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), Registries.SOUND_EVENT.getEntry(scream),
                SoundCategory.PLAYERS, VultureSuperCurseRules.SOUND_VOLUME, 1.0F, world.getRandom().nextLong());
    }

    private static void endRound(ServerWorld world) {
        Map<UUID, Long> curses = ACTIVE_CURSES.remove(world);
        if (curses == null) {
            return;
        }
        long now = world.getTime();
        if (curses.values().stream().anyMatch(end -> VultureSuperCurseRules.isCurseAudible(end, now))) {
            stopCurseSounds(world);
        }
    }

    /**
     * Stop-by-id reaches every instance of the clip on each client, so it goes to every player in the world (a listener
     * out of range at the press never received it, and the packet is harmless then).
     * 按 ID 停止会作用于客户端上该音效的所有实例，因此发给世界内所有玩家（按下时不在范围内的玩家本就没收到，停止包对其无害）。
     */
    private static void stopCurseSounds(ServerWorld world) {
        for (ServerPlayerEntity listener : world.getPlayers()) {
            for (Identifier soundId : CURSE_SOUND_IDS) {
                listener.networkHandler.sendPacket(new StopSoundS2CPacket(soundId, SoundCategory.PLAYERS));
            }
        }
    }
}

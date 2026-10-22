package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.component.partyanimal.PartygoerOwnerComponent;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.network.sabotage.SyncBlackoutSightS2CPacket;
import annina.sparkstrength.role.corruptcop.CorruptCopRules;
import annina.sparkstrength.role.partyanimal.PartygoerVisibilityRules;
import annina.sparkstrength.sabotage.BlackoutSightRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Client side of {@link BlackoutSightRules}. For a player the instinct key may not reveal, the highlight is evaluated
 * as if the key were released: every key-gated answer (Wathe's killer path, SparkTraits, faction policies, add-on
 * listeners) drops out, while outlines that show without the key survive untouched. Releasing the key, rather than
 * returning -1, is what keeps ability outlines alive; the same trick SparkWitch's USEC cloak uses.
 * BlackoutSightRules 的客户端部分。对本能键不能透视的玩家，按“未按本能键”重新计算描边：所有需要按键的结果（Wathe 杀手
 * 路径、SparkTraits、阵营策略、附属模组监听器）都会消失，而不用按键就显示的描边原样保留。之所以模拟松开按键而不是直接
 * 返回 -1，正是为了保留技能描边；SparkWitch 的 USEC 隐匿用的也是这种做法。
 *
 * <p>Render thread only. The viewer's team is cached per world tick, since outlines are asked for every entity every
 * frame and the trait and Wraith checks go through reflection.
 * 仅在渲染线程使用。观察者的阵营按世界 tick 缓存，因为每帧每个实体都要查询描边，而词条与冤魂判定都经过反射。</p>
 */
public final class BlackoutSightClient {
    private static boolean blackout;
    private static Set<UUID> linkedPartners = Set.of();
    /** Key-less evaluations in progress (Magician stand-ins nest). / 正在进行的“松开按键”计算层数（魔术师替身会嵌套）。 */
    private static int keyReleasedDepth;

    private static @Nullable World cachedWorld;
    private static long cachedTick = Long.MIN_VALUE;
    private static @Nullable UUID cachedViewer;
    private static boolean cachedExempt;
    private static BlackoutSightRules.Team cachedTeam = BlackoutSightRules.Team.ALONE;

    private BlackoutSightClient() {
    }

    public static void apply(SyncBlackoutSightS2CPacket packet) {
        blackout = packet.active();
        linkedPartners = Set.copyOf(packet.linkedPartners());
    }

    public static void reset() {
        blackout = false;
        linkedPartners = Set.of();
        keyReleasedDepth = 0;
        cachedWorld = null;
        cachedViewer = null;
    }

    /** Whether {@code key} must read as released right now. / 此刻 key 是否必须视为未按下。 */
    public static boolean releasesKey(KeyBinding key) {
        return keyReleasedDepth > 0 && key == WatheClient.instinctKeybind;
    }

    /** Whether Wathe's instinct gates must read false right now. / 此刻 Wathe 的本能入口是否必须返回 false。 */
    public static boolean releasesKey() {
        return keyReleasedDepth > 0;
    }

    public static int highlight(Entity target, IntSupplier original) {
        if (keyReleasedDepth > 0 || !restricts(target)) {
            return original.getAsInt();
        }
        keyReleasedDepth++;
        try {
            return original.getAsInt();
        } finally {
            keyReleasedDepth--;
        }
    }

    private static boolean restricts(Entity target) {
        if (blackout && target instanceof PartygoerEntity partygoer) {
            return restrictsPartygoer(partygoer);
        }
        if (!blackout || !(target instanceof PlayerEntity targetPlayer)) {
            return false;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || viewer.getUuid().equals(targetPlayer.getUuid())) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        refreshViewer(viewer, game);
        if (cachedExempt) {
            return false;
        }
        BlackoutSightRules.Team targetTeam = switch (cachedTeam) {
            // Only these teams compare roles; skip the faction lookup for the rest.
            // 只有这两个阵营需要比较身份；其余阵营跳过阵营查询。
            case WITCH, CORRUPT_COP -> targetTeam(game.getRole(targetPlayer));
            case KILLER, ALONE -> BlackoutSightRules.Team.ALONE;
        };
        return !BlackoutSightRules.keyMayReveal(true, false, cachedTeam, targetTeam,
                linkedPartners.contains(targetPlayer.getUuid()));
    }

    /**
     * A Partygoer counts as its owner ({@link PartygoerVisibilityRules#blackoutMayReveal}). Its owner only reaches
     * killer and spectator clients; elsewhere it reads null, which changes nothing: other teams never see a killer's
     * Partygoer in the dark and spectators are exempt.
     * 派对客按其召唤者处理（见 PartygoerVisibilityRules#blackoutMayReveal）。召唤者只会同步到杀手与旁观者客户端；其他客户端读到
     * null，这不影响结果：其他阵营在黑暗中本就看不到杀手的派对客，旁观者则不受限制。
     */
    private static boolean restrictsPartygoer(PartygoerEntity partygoer) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return false;
        }
        refreshViewer(viewer, GameWorldComponent.KEY.get(viewer.getWorld()));
        UUID owner = PartygoerOwnerComponent.ownerOf(partygoer);
        return !PartygoerVisibilityRules.blackoutMayReveal(true, cachedExempt, cachedTeam,
                viewer.getUuid().equals(owner), owner != null && linkedPartners.contains(owner));
    }

    private static void refreshViewer(ClientPlayerEntity viewer, GameWorldComponent game) {
        World world = viewer.getWorld();
        long tick = world.getTime();
        if (world == cachedWorld && tick == cachedTick && viewer.getUuid().equals(cachedViewer)) {
            return;
        }
        cachedWorld = world;
        cachedTick = tick;
        cachedViewer = viewer.getUuid();
        cachedExempt = !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || GameFunctions.isPlayerSpectatingOrCreative(viewer)
                || SparkWitchCompat.isWraithActive(viewer);
        Role role = game.getRole(viewer);
        cachedTeam = BlackoutSightRules.team(
                SparkTraitsCompat.isEffectiveKiller(role, viewer),
                CorruptCopRules.isCorruptCop(role) || CorruptCopRules.isInsider(role),
                SparkFactionCompat.isWitchFactionRole(role));
    }

    private static BlackoutSightRules.Team targetTeam(@Nullable Role role) {
        return BlackoutSightRules.team(
                role != null && role.canUseKiller(),
                CorruptCopRules.isCorruptCop(role) || CorruptCopRules.isInsider(role),
                SparkFactionCompat.isWitchFactionRole(role));
    }
}
